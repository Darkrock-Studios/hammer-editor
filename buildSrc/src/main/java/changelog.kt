package com.darkrockstudios.build

import org.intellij.markdown.flavours.commonmark.CommonMarkFlavourDescriptor
import org.intellij.markdown.parser.MarkdownParser

fun markdown() {
	val flavour = CommonMarkFlavourDescriptor()
	val parsedTree = MarkdownParser(flavour).buildMarkdownTreeFromString("")
}