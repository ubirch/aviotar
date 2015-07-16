FROM ubirch/java
MAINTAINER falko.zurell@gmail.com



ADD ${PWD}/target/${UBIRCH_SERVER_JAR} /opt/
ENTRYPOINT ["/usr/bin/java", "-jar" , "/opt/${UBIRCH_SERVER_JAR}"]
