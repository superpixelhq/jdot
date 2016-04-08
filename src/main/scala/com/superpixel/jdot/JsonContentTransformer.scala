package com.superpixel.jdot

import com.superpixel.jdot.json4s.JValueTransformer
import com.superpixel.jdot.pathing.JPathPair

trait JsonContentTransformer {

  def transform(json: String, attachments: List[Attachment] = Nil, localMerges: MergingJson = NoMerging, additionalInclusions: Inclusions = NoInclusions): String
  
  def transformList(jsonList: List[String], attachments: List[Attachment] = Nil, localMerges: MergingJson = NoMerging, additionalInclusions: Inclusions = NoInclusions): String
  
}

object JsonContentTransformer {
  
  def apply(fieldMap: Set[JPathPair], merges: MergingJson = NoMerging, inclusions: Inclusions = NoInclusions): JsonContentTransformer = JValueTransformer(fieldMap, merges, inclusions)
  
}