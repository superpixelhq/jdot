package com.superpixel.advokit.mapper;

import com.superpixel.jdot.Attachment;
import com.superpixel.jdot.Inclusions;
import com.superpixel.jdot.MergingJson;

import scala.collection.immutable.List;

public class JvJdotSettings {

	Inclusions inclusions;
	MergingJson mergingJson;
	scala.collection.immutable.List<Attachment> attachments;
	
	JvJdotSettings(Inclusions inclusions, MergingJson mergingJson,
			List<Attachment> attachments) {
		super();
		this.inclusions = inclusions;
		this.mergingJson = mergingJson;
		this.attachments = attachments;
	}
}