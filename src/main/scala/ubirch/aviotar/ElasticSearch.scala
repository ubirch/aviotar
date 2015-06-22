package ubirch.aviotar

import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date

import net.liftweb.json.JsonParser._
import net.liftweb.json.JValue
import net.liftweb.json.JsonDSL._
import net.liftweb.json.Serialization.{write, read}
import org.apache.http.client.methods.{HttpGet, HttpPost}
import org.apache.http.entity.{ContentType, StringEntity}
import org.apache.http.impl.client.HttpClients

import scala.concurrent.Future

/**
 * Add description.
 *
 * @author Matthias L. Jugel
 */
object ElasticSearch {

  import scala.concurrent.ExecutionContext.Implicits.global

  implicit val formats = net.liftweb.json.DefaultFormats

  val client = HttpClients.createDefault()

  def index(index: String, `type`: String, data: JValue): Future[JValue] = {
    Future {
      val df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ")
      val fields = (data \ "@timestamp").toOpt match {
        case Some(ts) => data
        case None => data.merge("@timestamp" -> df.format(new Date()): JValue)
      }

      val post = new HttpPost(s"http://localhost:9200/$index/${`type`}")
      post.setEntity(new StringEntity(write(fields), ContentType.APPLICATION_JSON))

      val response = client.execute(post)
      try {
        parse(new InputStreamReader(response.getEntity.getContent, "UTF-8"))
      } finally {
        response.close()
      }
    }
  }

  def get(index: String, `type`: String, query: String): Future[JValue] = {
    val get = new HttpGet(s"http://localhost:9200/$index/${`type`}?q=$query")

    Future {
      val response = client.execute(get)
      try {
        parse(new InputStreamReader(response.getEntity.getContent, "UTF-8"))
      } finally {
        response.close()
      }
    }
  }

}
