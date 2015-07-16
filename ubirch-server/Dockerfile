FROM ubirch/java
MAINTAINER falko.zurell@gmail.com

ENV UBIRCH_SERVER_JAR $UBIRCH_SERVER_JAR
RUN echo ${UBIRCH_SERVER_JAR}
COPY target/${UBIRCH_SERVER_JAR} /opt/ubirch-server.jar
COPY example.conf /opt/ubirch-server.conf
ENTRYPOINT ["/usr/bin/java", "-jar" , "/opt/ubirch-server.jar", "-c" , "/opt/ubirch-server.conf"]
