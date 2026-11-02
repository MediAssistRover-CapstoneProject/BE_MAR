FROM alpine:3.22
RUN apk add --no-cache openssh-client
COPY entrypoint.sh /usr/local/bin/mar-aws-tunnel
RUN sed -i 's/\r$//' /usr/local/bin/mar-aws-tunnel && chmod 755 /usr/local/bin/mar-aws-tunnel
ENTRYPOINT ["/usr/local/bin/mar-aws-tunnel"]
