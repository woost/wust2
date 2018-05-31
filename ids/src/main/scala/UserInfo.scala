package wust.ids

// this is the most generic representation of the user.
// in some places, we have a Post.User and in other, we have a AuthUser.
// To write methods that work with both, use UserInfo instead, which holds all information
// present in both data structures.
case class UserInfo(id: UserId, name: String, channelNodeId: NodeId)
