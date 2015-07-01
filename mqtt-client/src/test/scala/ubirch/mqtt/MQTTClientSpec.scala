/*
 * Copyright 2015 ubirch GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package ubirch.mqtt

import java.net.URI

import akka.actor.ActorSystem
import akka.testkit._
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import ubirch.mqtt.MQTTClient.{Message, Publish, Subscribe}

/**
 * Add description.
 *
 * @author Matthias L. Jugel
 */
class MQTTClientSpec
  extends TestKit(ActorSystem("MQTTSpec")) with ImplicitSender with WordSpecLike with Matchers with BeforeAndAfterAll {
  private val TEST_BROKER = new URI("tcp://test.mosquitto.org:1883")
  private val TEST_TOPIC = "ubirch.akka.mqtt-test"

  override def afterAll() {
    TestKit.shutdownActorSystem(system)
  }

  "mqtt client" must {
    "client must connect to test broker" in {
      val broker = TestActorRef(new MQTTClient(TEST_BROKER))

      val subscribe: Subscribe = Subscribe(TEST_TOPIC, self, 2)
      broker ! subscribe
      expectMsg(subscribe)

      val payload: Array[Byte] = "TEST-MSG".getBytes("UTF-8")
      val publish = Publish(TEST_TOPIC, payload)
      broker ! publish
      val message = expectMsgType[Message]
      message.payload shouldEqual payload
      message.topic shouldBe TEST_TOPIC
    }
  }

}
