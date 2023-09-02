package encyclopedia

import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryContent
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryType

fun fakeEntry() = EntryContent(
	id = 1,
	type = EntryType.PERSON,
	name = "Test Name",
	text = "Entry content",
	tags = setOf("tag1", "tag2")
)

fun entry1() = EntryContent(
	id = 1,
	type = EntryType.PERSON,
	name = "Entry 1",
	text = "This is a person entry",
	tags = setOf("tag1", "tag2")
)