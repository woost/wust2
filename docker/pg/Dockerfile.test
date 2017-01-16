FROM ubuntu:17.04

RUN apt-get update && apt-get install -y pgtap

ADD ./test.sh /test.sh
RUN chmod +x /test.sh

WORKDIR /

CMD ["/test.sh"]
ENTRYPOINT ["/test.sh"]
