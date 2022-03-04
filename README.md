# ✋ speakerq

speakerq is simple speaker queue web application. 

## Build

You will need Java 17, Maven 3.8, Node 17 and npm 8 to build this application.

```shell
./build.sh
```

## Run

```shell
java --enable-preview -jar target/speakerq-1.0-SNAPSHOT-fat.jar
```

Visit [localhost:8080](http://localhost:8080) to view application. You may start on another port by setting `PORT` environment variable.

## Develop

Start a watcher to rebuild frontend code on changes:
```shell
cd frontend
npm run watch
```

Start the backend application to load directly from the frontend folder by setting the `DEVELOPMENT` environment variable:
```shell
DEVELOPMENT=true java --enable-preview -jar target/speakerq-1.0-SNAPSHOT-fat.jar
```