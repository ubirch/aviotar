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

package ubirch.timeseries

import java.net.URI

import net.liftweb.json._
import org.scalatest.{FlatSpec, Matchers}
import ubirch.timeseries.components.{ElasticSearchStorageComponent, MapStorageComponent}

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.postfixOps

/**
 * Tests the timeseries storage.
 *
 * @author Matthias L. Jugel
 */
class StorageSpec extends FlatSpec with StorageSpecBahavior with Matchers {

  class MapTempStorage extends Storage[JValue] with MapStorageComponent[JValue] {
    override def timestamp(v: JValue): Long = ts2Long(v)
  }

  class ESTempStorage extends Storage[JValue] with ElasticSearchStorageComponent {
    override val timestamp = "@ts"
    val uri: URI = new URI("http://localhost:9200/ubirch-test/")

    Await.ready(delete("*"), 5 seconds)
  }

  "a simple map storage" should behave like storageComponent(new MapTempStorage)
  "the elasticsearch storage" should behave like storageComponent(new ESTempStorage)
}
