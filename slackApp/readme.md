- teams are workspaces
- channels, direct chats, ... are all called conversations
- messages are identified by channel id and timestamp (ts).
- messages which belong to a thread refer to their parent by its timestamp, they are still also in a conversation/channel
- a message is a thread parent, if it refers to itself as thread parent

