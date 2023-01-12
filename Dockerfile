### Build runtime image
FROM openjdk:8-jdk-alpine
ARG JAR_FILE=core-connector/target/*.jar
COPY ${JAR_FILE} app.jar

ENV MLCONN_OUTBOUND_ENDPOINT=http://pm4ml-mojaloop-connector:4001
ENV DFSP_NAME="i2i"
ENV DFSP_MOCKHOST="https://i2i.crossborderpayment.ubx.ph"
ENV DFSP_HOST="https://api.stg.i2i.ph"
ENV DFSP_USERNAME=username
ENV DFSP_PASSWORD=password
ENV DFSP_LOCALE=my
ENV DFSP_DATAMP=data
ENTRYPOINT ["java","-Ddfsp.mockhost=${DFSP_MOCKHOST}","-Ddfsp.host=${DFSP_HOST}","-Dml-outbound.endpoint=${MLCONN_OUTBOUND_ENDPOINT}","-Ddfsp.name=${DFSP_NAME}","-Ddfsp.username=${DFSP_USERNAME}","-Ddfsp.password=${DFSP_PASSWORD}","-Ddfsp.locale=${DFSP_LOCALE}","-Ddfsp.dataMap=${DFSP_DATAMP}","-jar","/app.jar"]
EXPOSE 3003