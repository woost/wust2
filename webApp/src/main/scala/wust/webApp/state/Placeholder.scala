package wust.webApp.state

trait Placeholder {
  def short: String
  def long: String
}
object Placeholder {
  val empty: Placeholder = new Placeholder {
    def short = ""
    def long = ""
  }
  def apply(str: String): Placeholder = new Placeholder {
    def short = str
    def long = str
  }
  def apply(short: String, long: String): Placeholder = {
    val s = short
    val l = long
    new Placeholder {
      def short = s
      def long = l
    }
  }

  def newMessage = Placeholder(short = "Write a Message", "Write a Message and press Enter to submit.")
  def newTask = Placeholder(short = "", "Press Enter to add")
  def newNote = Placeholder(short = "Add a Note", "Write your note here. Press Ctrl+Enter to save.")
  def newStage = Placeholder(short = "Add a Column", long = "Press Enter to add a Column.")
  def newSection = Placeholder(short = "Add a Section", long = "Press Enter to add a Section.")
  def newProject = Placeholder(short = "Add a Project", long = "Add a Project.")
}
