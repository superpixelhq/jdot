package com.superpixel.jdot

import com.superpixel.jdot.pathing.JPath
import com.superpixel.jdot.json4s.JValueAccessor

trait JsonContentAccessor {

  def getNumber(jPath: JPath): Option[Number]
  def getBoolean(jPath: JPath): Option[Boolean]
  def getString(jPath: JPath): Option[String]
  def getValueAsString(jPath: JPath): Option[String]
  def getJsonString(jPath: JPath): Option[String]
  
}

object JsonContentAccessor {
  import org.json4s.native.JsonMethods._
  def apply(json: String): JsonContentAccessor = JValueAccessor(parse(json));
  
}