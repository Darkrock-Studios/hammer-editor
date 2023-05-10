package com.darkrockstudios.apps.hammer.base.http.synchronizer

import com.appmattus.crypto.Algorithm
import com.appmattus.crypto.Digest
import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.base.http.ApiSceneType
import com.soywiz.kmem.Platform.Companion.isLittleEndian
import com.soywiz.krypto.encoding.Base64
import com.soywiz.krypto.encoding.base64Url
import kotlinx.datetime.Instant
import org.kotlincrypto.endians.BigEndian.Companion.toBigEndian

object EntityHasher {
	private fun buff() = ByteArray(4)

	fun hashScene(id: Int, order: Int, name: String, type: ApiSceneType, content: String): String {
		val buf = buff()
		val d = Algorithm.MurmurHash3_X64_128().createDigest()
		d.update(id, buf)
		d.update(order, buf)
		d.update(name, buf)
		d.update(type.ordinal, buf)
		d.update(content, buf)
		return d.digest().base64Url
	}

	fun hashNote(id: Int, created: Instant, content: String): String {
		val buf = buff()
		val d = Algorithm.MurmurHash3_X64_128().createDigest()
		d.update(id, buf)
		d.update(created.epochSeconds, buf)
		d.update(content, buf)
		return d.digest().base64Url
	}

	fun hashTimelineEvent(id: Int, order: Int, content: String, date: String?): String {
		val buf = buff()
		val d = Algorithm.MurmurHash3_X64_128().createDigest()
		d.update(id, buf)
		d.update(order, buf)
		d.update(content, buf)
		if (date != null) d.update(date, buf)
		return d.digest().base64Url
	}

	fun hashEncyclopediaEntry(
		id: Int,
		name: String,
		entryType: String,
		text: String,
		tags: List<String>,
		image: ApiProjectEntity.EncyclopediaEntryEntity.Image?
	): String {
		val buf = buff()
		val d = Algorithm.MurmurHash3_X64_128().createDigest()
		d.update(id, buf)
		d.update(name, buf)
		d.update(entryType, buf)
		d.update(text, buf)

		tags.forEach { tag ->
			d.update(tag, buf)
		}

		if (image != null) {
			d.update(Base64.decode(image.base64, url = true))
			d.update(image.fileExtension, buf)
		}

		return d.digest().base64Url
	}

	fun hashSceneDraft(id: Int, created: Instant, name: String, content: String): String {
		val buf = buff()
		val d = Algorithm.MurmurHash3_X64_128().createDigest()
		d.update(id, buf)
		d.update(created.epochSeconds, buf)
		d.update(name, buf)
		d.update(content, buf)
		return d.digest().base64Url
	}
}

private fun Digest<*>.update(string: String, buf: ByteArray = ByteArray(4)) {
	for (char in string) {
		update(char.code, buf)
	}
}

private fun Digest<*>.update(data: Int, buffer: ByteArray = ByteArray(4)) {
	buffer[0] = (data shr 0).toBigEndianByte()
	buffer[1] = (data shr 8).toBigEndianByte()
	buffer[2] = (data shr 16).toBigEndianByte()
	buffer[3] = (data shr 24).toBigEndianByte()

	update(buffer)
}

private fun Digest<*>.update(data: Long, buffer: ByteArray = ByteArray(4)) {
	buffer[0] = (data shr 0).toBigEndianByte()
	buffer[1] = (data shr 8).toBigEndianByte()
	buffer[2] = (data shr 16).toBigEndianByte()
	buffer[3] = (data shr 24).toBigEndianByte()
	update(buffer)

	buffer[0] = (data shr 32).toBigEndianByte()
	buffer[1] = (data shr 40).toBigEndianByte()
	buffer[2] = (data shr 48).toBigEndianByte()
	buffer[3] = (data shr 56).toBigEndianByte()
	update(buffer)
}

private inline fun Int.toBigEndianByte(): Byte {
	return if (isLittleEndian) {
		toBigEndian().toByte()
	} else {
		toByte()
	}
}

private inline fun Long.toBigEndianByte(): Byte {
	return if (isLittleEndian) {
		toBigEndian().toByte()
	} else {
		toByte()
	}
}