# aviotar

a cloud avatar library for the internet of things

## Libraries:

- [mqtt-client](mqtt-client) - Scala/Akka based MQTT client
- [timeseries-store](timseries-store) - ElasticSearch based timeseries storage
- [ubirch-testbed](ubirch-testbed) - Playground and Testbed

# PLAYGROUND

This is currently just a playground to get find common ground.

1. Check out this project.
2. Install and start elasticsearch locally (brew install elasticsearch)
3. Install and start mosquitto (brew install mosquitto)
4. Install and start kibana (https://www.elastic.co/products/kibana)
5. run mvn clean install
6. java -jar ubirch-testbed/target/ubirch-testbed-1.0.jar thingspeak-dumpdir 

To create the data dumps, you can use this simple script:
```bash
#! /bin/sh
for i in `seq 1 43`; do
  curl "http://api.ubirch.com/channels/$i/feed.json?start=2014-01-01%2000:00:00&end=2015-12-31%2000:00:00&key=XXXXXX" > $i.json
done
``` 
## LICENSE

    Copyright 2015 ubirch GmbH (http://www.ubirch.com)
    
    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at
    
        http://www.apache.org/licenses/LICENSE-2.0
    
    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.


