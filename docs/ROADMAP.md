# Roadmap

_**Note:** This is a one-man side project, so development velocity is slow and uneven. This roadmap is aspirational,
nothing
on here is a guaranteed, and I can't be sure when any of it will actually happen._

## Near-term

- **Client/Server syncing stabilization:** There's no known bugs, but this is by far the most likely feature to cause
  user data loss, so it must be rock solid
- ~~**Localization Support:** Move all strings into localization files, get setup with community localization service.~~
- ~~**Quality of Life UI improvements:** The UI is ready for a second polish pass, focusing on quality of life
  improvements.~~
	- ~~Word Count in text editor~~
	- ~~Draft selection editor~~
	- ~~Lots of work on Merge Conflict editors when syncing~~
    - ~~General work for larger screen sizes~~
    - ~~Confirm save on more entity types beyond just scenes~~

## Road to 1.0

- **Rich Text Editor 2.0:** This is one of the biggest weaknesses currently. The current text editor is buggy, has poor
  performance with lots of text, and only supports rich text through ugly hacks. We're waiting
  on `BasicTextfield2`
  coming later this year to build a more robust editor on.
	- Spell check
	- Possibly grammar check
- **Encyclopedia Improvements:** usability improvements
	- Allow more characters to be used in the name
	- ~~Allow tag removal, addition after creation~~
	- ~~Tag search in encyclopedia browser~~
- More unit testing across the board
- ~~Release for **MacOS**~~
- **Outlines:** write a short outline for each scene. Be able to see an overview of your story by
  reading only your
  outlines in order.
- **Scene Notes:** add extra notes to a scene, remind your self what story beats to hit, or what
  tone to strike.
- **Archive Scenes:** Alternative to deleting them, so they can be restored in the future.

## Post 1.0

- Release for **iOS**
- **Hemingway Mode:** Provide a distraction free writing experience. Very little UI, no spell check, ect.
- **Scene/Encyclopedia Integration:**
    - When you type the name of an entry in your encyclopedia, it will be hyper linked in-line to the entry.
    - See a summary of which characters or locations appear in the scene
- **Story Insights**: Which scene two characters first appeared in together, possibly other stuff pulling from
  encyclopedia and scenes
- **Encyclopedia 2.0:** Type specific templates, different fields for characters, locations, and more

## Further Future

- **Editor Requests:** Create a request for someone to edit a scene. A link will be generated, and
  they can read and suggest
  edits to the scene from a webpage. Then in-app, you can receive the edits and choose what to take.
- **Publish on Web:** Publish a story as a simple webview you can link people to.
