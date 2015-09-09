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

package ubirch.aviotar

import java.io.File
import javax.imageio.ImageIO

/**
 * Add description.
 *
 * @author Matthias L. Jugel
 */
object BitmapFontConverter extends App {
  val image = ImageIO.read(new File(args(0)))
  val charHeight = args(1).toInt
  val charWidth = args(2).toInt
  val skipWidth = args(3).toInt

  implicit def bool2int(b:Boolean): Int = if (b) 1 else 0

  println("%dx%d".format(image.getWidth, image.getHeight))
  var x = 0
  while(x < image.getWidth - 1) {
    val width = /*if (x == 8*(charWidth + skipWidth)) charWidth-2 else*/ charWidth
    var charStr: Seq[String] = Seq()
    for(c <- 0 until width) {
      print("        // ")
      var bits = 0x00
      for (r <- 0 until charHeight) {
//        println("%x".format(image.getRGB(x + c, r)))
        bits |= (image.getRGB(x + c, r) != 0xffffffff) << r

        if (image.getRGB(x + c, r) != 0xffffffff) print("*") else print(" ")
      }
      charStr :+= "0x%02x".format(bits)
      println()
    }
    x += width + skipWidth
    println(charStr.mkString(",")+",")
  }
}
