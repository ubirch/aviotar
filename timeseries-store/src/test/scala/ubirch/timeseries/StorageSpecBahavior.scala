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

import java.text.SimpleDateFormat
import java.util.{Date, Locale, TimeZone}

import net.liftweb.json.JValue
import net.liftweb.json.JsonDSL._
import org.scalatest.{FlatSpec, Matchers}

import scala.annotation.tailrec
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.language.postfixOps

/**
 * Behaviour for the timeseries storage specification.
 *
 * @author Matthias L. Jugel
 */
trait StorageSpecBahavior {
  this: FlatSpec with Matchers =>

  implicit val context = scala.concurrent.ExecutionContext.Implicits.global
  implicit val formats = net.liftweb.json.DefaultFormats

  val tsFormat = new ThreadLocal[SimpleDateFormat]() {
    override def initialValue() = {
      val df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.ENGLISH)
      df.setTimeZone(TimeZone.getTimeZone("UTC"))
      df
    }
  }

  def ts2Long(v: JValue): Long = tsFormat.get.parse((v \ "@ts").extract[String]).getTime

  def checkOrdering(l: Iterable[JValue], extract: JValue => Double): Boolean = {
    l.view.zip(l.tail).forall(x => extract(x._1) > extract(x._2))
  }

  // TODO wrap into a callable and make cancelable
  @tailrec private def require[T](task: => Future[T], check: T => Boolean, max: Int = 5): T = {
    val r = Await.result(task, 1 second)
    if (max == 0 || check(r)) return r
    Thread.sleep(500)
    require(task, check, max - 1)
  }

  def storageComponent(createStorage: => Storage[JValue]) {
    it should "store and retrieve a value" in {
      val storage = createStorage
      val data: JValue = ("@ts" -> "2015-01-01T00:11:22.333+0200") ~ ("v" -> 3.1415)

      val s = Await.result(storage.store("test", data), 1 seconds)
      (s \ "@ts").extract[String] shouldBe "2015-01-01T00:11:22.333+0200"
      (s \ "v").extract[Double] shouldBe 3.1415

      val r = require[Option[JValue]](storage.fetch("test"), _.nonEmpty)
      r.isInstanceOf[Some[JValue]] shouldBe true
      (r.get \ "@ts").extract[String] shouldBe "2015-01-01T00:11:22.333+0200"
      (r.get \ "v").extract[Double] shouldBe 3.1415
    }

    it should "store multiple values and be able to fetch them" in {
      val storage = createStorage
      val values = Await.result(Future.sequence((1 to 10).map { i =>
        val data = ("@ts" -> tsFormat.get.format(new Date(i.toLong))) ~ ("v" -> (i + 3.1415))
        storage.store("test", data)
      }), 5 seconds)
      values.size shouldBe 10

      val r = require[Option[JValue]](storage.fetch("test"), _.nonEmpty)
      r.isInstanceOf[Some[JValue]] shouldBe true
      (r.get \ "@ts").extract[String] shouldBe "1970-01-01T00:00:00.010+0000"
      (r.get \ "v").extract[Double] shouldBe 13.1415

      val t = require[Iterable[JValue]](storage.fetch("test", 20), _.nonEmpty)
      t.size shouldBe 10
      checkOrdering(t, ts2Long) shouldBe true
      checkOrdering(t, v => (v \ "v").extract[Double]) shouldBe true
    }

    it should "fetch a range of values" in {
      val storage = createStorage
      val values = Await.result(Future.sequence((1 to 10).map { i =>
        val data = ("@ts" -> tsFormat.get.format(new Date(i.toLong))) ~ ("v" -> (3.1415 + i))
        storage.store("test", data)
      }), 5 seconds)
      values.size shouldBe 10

      val t = require[Iterable[JValue]](storage.fetch("test", 5), _.nonEmpty)
      t.size shouldBe 5
      checkOrdering(t, ts2Long) shouldBe true
      checkOrdering(t, v => (v \ "v").extract[Double]) shouldBe true

      val r = require[Iterable[JValue]](storage.fetch("test", 5, 10), _.nonEmpty)
      r.size shouldBe 5
      (r.head \ "v").extract[Double] shouldBe 12.1415
      (r.takeRight(1).head \ "v").extract[Double] shouldBe 8.1415
      checkOrdering(r, ts2Long) shouldBe true
      checkOrdering(r, v => (v \ "v").extract[Double]) shouldBe true
    }

    it should "delete a whole data set" in {
      val storage = createStorage
      val data: JValue = ("@ts" -> "2015-01-01T00:11:22.333+0200") ~ ("v" -> 3.1415)
      val s = Await.result(storage.store("test", data), 1 seconds)
      (s \ "@ts").extract[String] shouldBe "2015-01-01T00:11:22.333+0200"
      (s \ "v").extract[Double] shouldBe 3.1415

      Await.result(storage.delete("test"), 1 seconds) shouldBe true
      Await.result(storage.fetch("test"), 1 seconds) shouldBe None
    }

    it should "fail on missing values" in {
      val storage = createStorage

      Await.result(storage.fetch("test"), 1 seconds) shouldBe None
      Await.result(storage.fetch("test", 10), 1 seconds) shouldBe Iterable.empty
      Await.result(storage.fetch("test", 1L, 10L), 1 seconds) shouldBe Iterable.empty

      Await.result(storage.delete("test"), 1 seconds) shouldBe true
    }
  }
}
