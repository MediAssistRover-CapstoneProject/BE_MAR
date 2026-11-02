#!/bin/sh
set -eu
umask 077
# Windows bind mounts can expose 0777 modes. OpenSSH must read a private Linux
# copy; the original key stays read-only and never enters the Docker image.
mkdir -p /tmp/mar-ssh
cat /run/ssh/ec2-key.pem > /tmp/mar-ssh/id
chmod 600 /tmp/mar-ssh/id
exec ssh -N -T -i /tmp/mar-ssh/id \
  -o BatchMode=yes -o IdentitiesOnly=yes -o StrictHostKeyChecking=yes \
  -o UserKnownHostsFile=/run/ssh/known_hosts \
  -o ExitOnForwardFailure=yes -o ConnectTimeout=8 \
  -o ServerAliveInterval=10 -o ServerAliveCountMax=3 \
  -L 0.0.0.0:3306:127.0.0.1:3306 \
  -p "${AWS_SSH_PORT:-22}" "${AWS_SSH_USERNAME:?}@${AWS_SSH_HOST:?}"
