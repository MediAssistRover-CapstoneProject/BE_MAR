'use strict';

const $ = id => document.getElementById(id);
const uuid = /^[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}$/i;
let token = '', jobId = '', selectedStep = null, action = null, qrUntil = 0;
let generation = 0, timer, qrTimer, responseKey = '', pending = false, respondedStep = '';
let staff = null, endpoints = [], catalogLoaded = false, catalogLoading = false, draft = null;

async function api(path, body, key) {
  const response = await fetch(path, {
    method: body === undefined ? 'GET' : 'POST', cache: 'no-store',
    headers: {...(token ? {Authorization: 'Bearer ' + token} : {}),
      ...(body === undefined ? {} : {'Content-Type': 'application/json'}),
      ...(key ? {'Idempotency-Key': key} : {})},
    body: body === undefined ? undefined : JSON.stringify(body), signal: AbortSignal.timeout(8000)
  });
  const data = response.status === 204 ? null : await response.json().catch(() => null);
  if (!response.ok) throw Object.assign(new Error(data?.message || 'Yêu cầu không thành công.'), {status: response.status});
  return data?.data ?? data;
}

function message(text) { $('message').textContent = text; }
function deliveryMessage(text) { $('deliveryMessage').textContent = text; }

async function allPages(path) {
  const rows = [];
  for (let page = 0, totalPages = 1; page < totalPages; page++) {
    const result = await api(path + '?page=' + page + '&size=100');
    rows.push(...result.content);
    totalPages = result.totalPages;
  }
  return rows;
}

function updateDeliveryEndpoints() {
  const map = $('deliveryMap').value;
  const available = endpoints.filter(endpoint => endpoint.mapId === map);
  for (const id of ['pickupEndpoint']) {
    $(id).replaceChildren(new Option('Chọn endpoint', ''), ...available.map(endpoint =>
      new Option(endpoint.endpointCode + ' · ' + endpoint.endpointName, endpoint.endpointId)));
    $(id).disabled = !available.length;
  }
  $('createDeliveryButton').disabled = !available.length;
  loadOnDuty().catch(error => deliveryMessage(error.message));
  deliveryMessage(map && !available.length ? 'Bản đồ này chưa có endpoint khả dụng.' : '');
}

async function loadDeliveryCatalog() {
  if (!$('deliverySetup').open || !staff || catalogLoaded || catalogLoading) return;
  const currentToken = token;
  catalogLoading = true;
  deliveryMessage('Đang tải bản đồ và endpoint…');
  try {
    const [maps, points] = await Promise.all([allPages('/api/v1/maps'), allPages('/api/v1/endpoints')]);
    if (token !== currentToken) return;
    const active = maps.filter(map => map.status === 'ACTIVE');
    endpoints = points.filter(point => point.active && point.accessible);
    $('deliveryMap').replaceChildren(new Option('Chọn bản đồ', ''), ...active.map(map =>
      new Option(map.mapName + ' · v' + map.versionNo, map.mapId)));
    if (active.length === 1) $('deliveryMap').value = active[0].mapId;
    catalogLoaded = true;
    updateDeliveryEndpoints();
    if (!active.length) deliveryMessage('Chưa có bản đồ đang hoạt động.');
  } catch (error) {
    if (token === currentToken) deliveryMessage(error.message + ' Đóng và mở lại phần tạo yêu cầu để tải lại.');
  } finally {
    if (token === currentToken) catalogLoading = false;
  }
}

function resetDeliveryForm() {
  draft = null;
  $('createDelivery').reset();
  $('receiverShift').replaceChildren(new Option('Chọn staff đang trực', ''));
  $('receiverAvatar').hidden = true;
  $('deliveryFields').disabled = false;
  $('sendDelivery').hidden = $('resetDelivery').hidden = true;
  $('sendDelivery').textContent = 'Gửi robot đi lấy hàng';
  updateDeliveryEndpoints();
}

$('deliverySetup').addEventListener('toggle', loadDeliveryCatalog);
$('deliveryMap').addEventListener('change', updateDeliveryEndpoints);
$('resetDelivery').addEventListener('click', resetDeliveryForm);
$('createDelivery').addEventListener('submit', async event => {
  event.preventDefault();
  if (!staff || draft?.requestId || $('deliveryFields').disabled) return;
  const selected = dutyStaff.find(row => row.shiftId === $('receiverShift').value);
  const receiver = selected?.accountId || '';
  const name = selected?.fullName || '', item = $('deliveryItem').value.trim();
  const quantity = Number($('deliveryQuantity').value);
  if (!uuid.test(receiver) || !name || !item || !Number.isSafeInteger(quantity) || quantity < 1 || quantity > 2147483647) {
    deliveryMessage('Nhập ID staff hợp lệ, tên người nhận, tên hàng và số lượng.');
    return;
  }
  const body = {
    mapId: $('deliveryMap').value, pickupEndpointId: $('pickupEndpoint').value,
    receiverShiftId: selected.shiftId, targetMode: 'ENDPOINT',
    expectedSenderAccountId: staff.accountId, expectedReceiverAccountId: receiver,
    recipientName: name, requiresConfirmation: true, priority: 50,
    items: [{itemCode: 'ITEM-01', itemName: item, itemCategory: 'GENERAL', quantity, unit: 'kiện', sensitive: false}]
  };
  const signature = JSON.stringify(body);
  if (draft?.signature !== signature) draft = {signature, key: crypto.randomUUID()};
  const attempt = draft;
  $('deliveryFields').disabled = true;
  deliveryMessage('Đang tạo yêu cầu…');
  try {
    const created = await api('/api/v1/delivery-requests', body, attempt.key);
    if (draft !== attempt) return;
    attempt.requestId = created.request.serviceRequestId;
    $('sendDelivery').hidden = false;
    $('sendDelivery').disabled = false;
    deliveryMessage('Đã tạo yêu cầu. Bấm Gửi robot để bắt đầu job.');
  } catch (error) {
    if (draft === attempt) {
      $('deliveryFields').disabled = false;
      deliveryMessage(error.message);
    }
  }
});

async function deliveryJob(requestId) {
  const page = await api('/api/v1/jobs?size=1&requestId=' + requestId);
  return page.content[0];
}

$('sendDelivery').addEventListener('click', async event => {
  const button = event.currentTarget, attempt = draft;
  if (!attempt?.requestId || button.disabled) return;
  button.disabled = true;
  deliveryMessage('Đang gửi yêu cầu tới robot…');
  try {
    let job;
    if (!attempt.submitted) {
      try { await api('/api/v1/service-requests/' + attempt.requestId + '/submit', {}); }
      catch (error) {
        // Recover the same Job if the request committed before the response was lost.
        if (draft !== attempt) return;
        job = await deliveryJob(attempt.requestId);
        if (!job) throw error;
      }
      attempt.submitted = true;
    }
    if (draft !== attempt) return;
    job ??= await deliveryJob(attempt.requestId);
    if (draft !== attempt) return;
    if (!job?.jobId) throw new Error('Đã gửi yêu cầu, đang chờ Job ID. Bấm tìm lại job.');
    button.hidden = true;
    $('resetDelivery').hidden = false;
    deliveryMessage('Đã gửi robot · Job ' + job.jobId);
    followJob(job.jobId);
  } catch (error) {
    if (draft === attempt) {
      if (attempt.submitted) button.textContent = 'Tìm lại job';
      deliveryMessage(error.message);
    }
  } finally {
    if (draft === attempt) button.disabled = false;
  }
});

function clearQr() {
  clearTimeout(qrTimer);
  qrTimer = undefined;
  qrUntil = 0;
  $('qrPanel').hidden = true;
  $('qr').width = 1;
}

function staffAction(step) {
  const input = step?.resolvedInputsJson ?? {};
  if (step?.stepTypeSnapshot === 'HANDLE_PAYLOAD' && Array.isArray(input.items)) {
    return {label: input.action === 'LOAD' ? 'Đã đặt đủ hàng vào ngăn' : 'Đã lấy đủ hàng khỏi ngăn',
      responseType: 'PAYLOAD_CONFIRMED', data: {confirmed: true, items: input.items}};
  }
  if (step?.stepTypeSnapshot === 'INTERACT' && ['CLOSE_SENDER', 'CLOSE_RECEIVER'].includes(input.prompt_key))
    return {label: 'Đã đóng nắp và để tay ra khỏi cơ cấu khóa', responseType: 'DOOR_CLOSED', data: {closed: true}};
  if (step?.stepTypeSnapshot === 'INTERACT' && ['VERIFY_SENDER', 'VERIFY_RECEIVER'].includes(input.prompt_key))
    return {label: 'Đã kiểm tra ngăn khóa chắc', responseType: 'LOCK_CHECKED', data: {locked: true}};
  return null;
}

function render(detail) {
  const job = detail.job;
  const step = job.status === 'RUNNING' && !job.cancelRequestedAt
    ? detail.steps.find(s => s.jobStepId === job.selectedJobStepId && s.status === 'RUNNING') : null;
  if (selectedStep?.jobStepId !== step?.jobStepId) {
    clearQr();
    responseKey = '';
  }
  selectedStep = step;
  const phase = step?.stepCodeSnapshot?.match(/_(SENDER|RECEIVER)$/)?.[1]?.toLowerCase();
  const assigned = phase && job.inputJson?.['expected_' + phase + '_account_id'];
  const myTurn = !assigned || assigned === staff?.accountId;
  action = myTurn ? staffAction(step) : null;
  const qrStep = myTurn && step?.stepTypeSnapshot === 'AUTHENTICATE' && step.resolvedInputsJson?.method === 'QR_CODE';
  $('stage').textContent = job.status === 'SUCCEEDED' ? 'Giao nhận đã hoàn tất.'
    : job.status === 'RECOVERY_REQUIRED' ? 'Cần nhân viên kiểm tra robot và ngăn hàng.'
    : !myTurn ? 'Đang chờ staff tại điểm ' + (phase === 'sender' ? 'lấy hàng.' : 'giao hàng.')
    : qrStep ? 'Robot đang chờ quét QR của staff được giao.'
    : action?.label || 'Trạng thái nhiệm vụ · ' + job.status;
  if (qrUntil && qrUntil <= Date.now()) clearQr();
  $('showQr').hidden = !qrStep || !!qrUntil;
  $('showQr').disabled = pending;
  $('confirm').hidden = !action;
  $('confirm').disabled = pending || respondedStep === step?.jobStepId;
  if (action) $('confirm').textContent = action.label;
  $('items').hidden = !action?.data.items;
  $('items').replaceChildren(...(action?.data.items ?? []).map(item => {
    const line = document.createElement('li');
    line.textContent = item.item_code + ' · ' + item.quantity;
    return line;
  }));
}

async function refresh(epoch) {
  let finished = false;
  try {
    const detail = await api('/api/v1/jobs/' + jobId);
    if (epoch === generation) {
      render(detail);
      finished = ['SUCCEEDED', 'FAILED', 'CANCELLED'].includes(detail.job.status);
    }
  } catch (error) {
    if (epoch !== generation) return;
    if ([401, 403, 404, 410].includes(error.status)) {
      clearQr();
      $('showQr').hidden = $('confirm').hidden = true;
      if (error.status === 401) { signOut(); message('Phiên đăng nhập đã hết hạn.'); return; }
      message(error.message);
    } else {
      // A failed poll is not evidence that a signed, unexpired QR was revoked.
      // Its expiry timer and the next authoritative step update still clear it.
      if (qrUntil && qrUntil <= Date.now()) clearQr();
      $('showQr').disabled = $('confirm').disabled = true;
      message('Đang kết nối lại trạng thái nhiệm vụ. QR còn hạn được giữ nguyên.');
    }
  } finally {
    if (!finished && epoch === generation && token && jobId) timer = setTimeout(() => refresh(epoch), 2000);
  }
}

$('login').addEventListener('submit', async event => {
  event.preventDefault();
  const button = event.currentTarget.querySelector('button');
  button.disabled = true;
  message('');
  try {
    const password = $('password').value;
    $('password').value = '';
    token = (await api('/api/v1/auth/login', {username: $('username').value.trim(), password})).accessToken;
    const account = await api('/api/v1/auth/me');
    if (account.role !== 'STAFF') throw new Error('Dùng tài khoản staff được giao nhiệm vụ.');
    staff = account;
    $('staffName').textContent = account.fullName || account.username;
    $('staffId').textContent = account.accountId;
    resetDeliveryForm();
    $('loginSection').hidden = true;
    $('jobSection').hidden = false;
    startNotices();
    if (uuid.test($('jobId').value.trim())) followJob($('jobId').value.trim());
  } catch (error) {
    token = '';
    message(error.message);
  } finally { button.disabled = false; }
});

function followJob(value) {
  clearTimeout(timer);
  clearQr();
  selectedStep = action = null;
  responseKey = respondedStep = '';
  $('showQr').hidden = $('confirm').hidden = $('items').hidden = true;
  message('');
  $('jobId').value = value;
  $('copyJobLink').hidden = false;
  jobId = value;
  refresh(++generation);
}

$('watch').addEventListener('submit', event => {
  event.preventDefault();
  const value = $('jobId').value.trim();
  if (!uuid.test(value)) { message('Nhập Job ID hợp lệ.'); return; }
  followJob(value);
});

$('copyJobLink').addEventListener('click', async () => {
  if (!jobId) return;
  const link = new URL('/delivery-qr.html', location.origin);
  link.searchParams.set('jobId', jobId);
  try {
    await navigator.clipboard.writeText(link.href);
    message('Đã sao chép liên kết giao nhận.');
  } catch {
    message('Gửi Job ID đang hiển thị cho staff nhận.');
  }
});

$('showQr').addEventListener('click', async () => {
  if (pending || !selectedStep) return;
  pending = $('showQr').disabled = true;
  message('');
  const epoch = generation;
  try {
    const code = await api('/api/v1/jobs/' + jobId + '/staff-phone-qr', {});
    if (epoch !== generation || code.authJobStepId !== selectedStep?.jobStepId) return;
    const size = code.rows.length;
    const until = Date.parse(code.expiresAt);
    if (size < 21 || size > 120 || !code.rows.every(row => row.length === size && /^[01]+$/.test(row))
        || !Number.isFinite(until) || until <= Date.now()) throw new Error('Mã QR đã hết hạn hoặc không hợp lệ.');
    const canvas = $('qr'), pixels = 8;
    canvas.width = canvas.height = size * pixels;
    const context = canvas.getContext('2d');
    context.fillStyle = '#fff';
    context.fillRect(0, 0, canvas.width, canvas.height);
    context.fillStyle = '#000';
    code.rows.forEach((row, y) => [...row].forEach((value, x) => {
      if (value === '1') context.fillRect(x * pixels, y * pixels, pixels, pixels);
    }));
    qrUntil = until;
    clearTimeout(qrTimer);
    qrTimer = setTimeout(() => {
      if (epoch !== generation || qrUntil !== until) return;
      clearQr();
      message('QR đã hết hạn. Đang chờ cập nhật bước xác thực từ hệ thống.');
    }, until - Date.now());
    $('qrExpiry').textContent = 'Đưa màn hình trước camera robot. Mã hết hạn lúc ' + new Date(until).toLocaleTimeString('vi-VN');
    $('qrPanel').hidden = false;
    $('showQr').hidden = true;
  } catch (error) {
    if (epoch === generation) message(error.message);
  } finally { pending = $('showQr').disabled = false; }
});

$('confirm').addEventListener('click', async () => {
  if (pending || !selectedStep || !action) return;
  pending = $('confirm').disabled = true;
  message('');
  const epoch = generation, id = selectedStep.jobStepId;
  try {
    responseKey ||= crypto.randomUUID();
    const {responseType, data} = action;
    await api('/api/v1/job-steps/' + id + '/responses', {responseType, data}, responseKey);
    if (epoch === generation) { respondedStep = id; message('Đã ghi nhận xác nhận.'); }
  } catch (error) {
    if (epoch === generation) message(error.message);
  } finally {
    pending = false;
    $('confirm').disabled = respondedStep === selectedStep?.jobStepId;
  }
});

async function signOut() {
  stopNotices();
  generation++;
  clearTimeout(timer);
  token = jobId = responseKey = respondedStep = '';
  staff = null;
  dutyStaff = [];
  $('profileForm').reset();$('ownShifts').replaceChildren();$('profileAvatar').hidden=true;$('profileAvatar').removeAttribute('src');$('avatarFile').value='';
  $('staffToolsMessage').textContent='';$('emailStatus').textContent='';
  endpoints = [];
  catalogLoaded = catalogLoading = false;
  $('deliverySetup').open = false;
  $('deliveryMap').replaceChildren();
  $('copyJobLink').hidden = true;
  resetDeliveryForm();
  selectedStep = action = null;
  clearQr();
  $('jobSection').hidden = true;
  $('loginSection').hidden = false;
  message('');
}
$('signOut').addEventListener('click', signOut);
const sharedJob = new URLSearchParams(location.search).get('jobId');
if (sharedJob && uuid.test(sharedJob)) $('jobId').value = sharedJob;

let dutyStaff = [], noticeAbort, noticeRetry, previewUrl;
async function loadOnDuty() {
  const map = $('deliveryMap').value, sessionToken = token;
  if (!map || !staff) {dutyStaff=[];$('receiverShift').replaceChildren(new Option('Chọn staff đang trực',''));$('receiverAvatar').hidden=true;return;}
  const rows = [];
  for (let page = 0, pages = 1; page < pages; page++) {
    const result = await api('/api/v1/staff/on-duty?mapId=' + encodeURIComponent(map) + '&size=100&page=' + page);
    rows.push(...result.content); pages = result.totalPages;
  }
  if (token !== sessionToken || $('deliveryMap').value !== map) return;
  dutyStaff = rows.filter(row => row.accountId !== staff.accountId);
  const previous = $('receiverShift').value;
  $('receiverShift').replaceChildren(new Option('Chọn staff đang trực', ''), ...dutyStaff.map(row => {
    const option = new Option(row.fullName + ' · ' + row.roomCode + ' · đến ' + new Date(row.endsAt).toLocaleTimeString() + (row.deliverable ? '' : ' · ' + row.reason), row.shiftId);
    option.disabled = !row.deliverable; return option;
  }));
  if (dutyStaff.some(row => row.shiftId === previous && row.deliverable)) $('receiverShift').value = previous;
}
$('receiverShift').onchange=()=>{const selected=dutyStaff.find(s=>s.shiftId===$('receiverShift').value);$('receiverAvatar').hidden=!selected?.avatarUrl;if(selected?.avatarUrl)$('receiverAvatar').src=selected.avatarUrl;};
$('reloadStaff').onclick = () => loadOnDuty().catch(e => deliveryMessage(e.message));
async function staffRequest(path, method, body) {
  const requestToken = token;
  const multipart = body instanceof FormData;
  const response = await fetch(path, {method, headers: {Authorization: 'Bearer ' + token, ...(!multipart && body ? {'Content-Type':'application/json'} : {})}, body: body ? multipart ? body : JSON.stringify(body) : undefined});
  const data = await response.json().catch(() => null);
  if (token !== requestToken) throw new Error('Phiên đăng nhập đã thay đổi');
  if (!response.ok) throw new Error(data?.message || 'Yêu cầu không thành công');
  return data?.data ?? data;
}
function showProfile(data) {
  staff = data.account; $('staffName').textContent = staff.fullName || staff.username;
  $('profileName').value = data.account.fullName || '';
  $('profileEmail').value = data.account.email || '';
  $('profilePhone').value = data.account.phone || '';
  $('profileExtension').value = data.staffProfile.contactExtension || '';
  const url = data.staffProfile.url;
  $('profileAvatar').hidden = !url;
  if (url) $('profileAvatar').src = url; else $('profileAvatar').removeAttribute('src');
}
async function loadStaffTools() {
  const requestToken=token;
  const data=await api('/api/v1/staff/me');
  const shifts = await allPages('/api/v1/staff/shifts');
  if(token!==requestToken)return;
  showProfile(data);
  $('ownShifts').replaceChildren(...shifts.filter(s => ['PLANNED','ACTIVE'].includes(s.status)).map(s => {
    const row = document.createElement('p'); row.textContent = s.shiftName + ' · ' + s.roomCode + ' · ' + new Date(s.startsAt).toLocaleString() + ' — ' + new Date(s.endsAt).toLocaleString() + ' ';
    for (const [action, label] of [['check-in','Check-in'],['check-out','Check-out']]) {
      const button = document.createElement('button'); button.type = 'button'; button.textContent = label;
      button.disabled = action === 'check-in' ? !!s.checkedInAt || !!s.checkedOutAt || Date.now() < Date.parse(s.startsAt) || Date.now() >= Date.parse(s.endsAt) : !s.checkedInAt || !!s.checkedOutAt;
      button.onclick = async () => {button.disabled = true; try {await api('/api/v1/staff/shifts/' + s.shiftId + '/' + action, {}); $('staffToolsMessage').textContent = label + ' thành công'; await loadStaffTools(); await loadOnDuty();} catch(e) {$('staffToolsMessage').textContent = e.message;} finally {button.disabled = false;}};
      row.append(button);
    } return row;
  }));
}
$('loadStaffTools').onclick = () => loadStaffTools().catch(e => $('staffToolsMessage').textContent = e.message);
$('profileForm').onsubmit = async e => {e.preventDefault();try {
  showProfile(await staffRequest('/api/v1/staff/me','PATCH',{fullName:$('profileName').value,email:$('profileEmail').value,phone:$('profilePhone').value,contactExtension:$('profileExtension').value}));
  $('staffToolsMessage').textContent = 'Đã lưu hồ sơ';
} catch(error) {$('staffToolsMessage').textContent = error.message;}};
$('avatarFile').onchange = () => {if(previewUrl)URL.revokeObjectURL(previewUrl);const file=$('avatarFile').files[0];if(file){previewUrl=URL.createObjectURL(file);$('profileAvatar').src=previewUrl;$('profileAvatar').hidden=false;}};
$('uploadAvatar').onclick = async () => {try {const file=$('avatarFile').files[0];if(!file)throw new Error('Chọn ảnh trước');const body=new FormData();body.append('file',file);showProfile(await staffRequest('/api/v1/staff/me/avatar','POST',body));$('staffToolsMessage').textContent='Đã lưu ảnh trên S3';}catch(e){$('staffToolsMessage').textContent=e.message;}};
$('deleteAvatar').onclick = async () => {try{showProfile(await staffRequest('/api/v1/staff/me/avatar','DELETE'));}catch(e){$('staffToolsMessage').textContent=e.message;}};
function renderNotices(rows) {
  $('deliveryNotifications').replaceChildren(...rows.map(row => {
    const li=document.createElement('li'), button=document.createElement('button');
    button.type='button';button.textContent=row.message+' · '+new Date(row.createdAt).toLocaleString();
    button.onclick=()=>{const id=row.dataJson?.jobId;if(uuid.test(id || ''))followJob(id);};li.append(button);return li;
  }));
}
function stopNotices(){noticeAbort?.abort();clearTimeout(noticeRetry);$('deliveryNotifications').replaceChildren();}
async function startNotices(){
  stopNotices(); if(!token)return; const currentToken=token;noticeAbort=new AbortController();
  try {
    const response=await fetch('/api/v1/staff/notifications/events',{headers:{Authorization:'Bearer '+currentToken},signal:noticeAbort.signal});
    if(!response.ok)throw new Error('Không kết nối được thông báo');
    const reader=response.body.getReader(),decoder=new TextDecoder();let buffer='';
    while(true){const {value,done}=await reader.read();if(done)break;buffer+=decoder.decode(value,{stream:true}).replace(/\r/g,'');let index;
      while((index=buffer.indexOf('\n\n'))>=0){const event=buffer.slice(0,index);buffer=buffer.slice(index+2);const data=event.split('\n').filter(line=>line.startsWith('data:')).map(line=>line.slice(5)).join('\n');if(data&&token===currentToken)renderNotices(JSON.parse(data));}
    }
  }catch(e){if(e.name==='AbortError')return;$('emailStatus').textContent='Đang kết nối lại thông báo…';}
  if(token===currentToken&&token)noticeRetry=setTimeout(startNotices,3000);
}
