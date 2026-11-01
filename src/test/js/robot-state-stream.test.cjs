const {test}=require('node:test');
const assert=require('node:assert/strict');
const {consumeRobotEvents}=require('../../main/resources/static/map-navigation.js');
test('authenticated fetch stream decodes fragmented UTF-8 and CRLF without treating comments as state',async()=>{
  const message=': keepalive\r\n\r\nevent: robot-state\r\ndata: '+JSON.stringify({robot:{robotId:'r1'},reason:'Đã định vị'})+'\r\n\r\n';
  const bytes=new TextEncoder().encode(message),events=[];
  const response=new Response(new ReadableStream({start(controller){for(let i=0;i<bytes.length;i+=3)controller.enqueue(bytes.slice(i,i+3));controller.close();}}));
  await consumeRobotEvents(response,x=>events.push(x));
  assert.equal(events.length,1);assert.equal(events[0].robot.robotId,'r1');assert.equal(events[0].reason,'Đã định vị');
});
test('malformed stream is rejected so the caller can fall back to REST, never inventing a pose',async()=>{
  const response=new Response('event: robot-state\ndata: invalid\n\n');
  await assert.rejects(consumeRobotEvents(response,()=>assert.fail('invalid data accepted')));
});
