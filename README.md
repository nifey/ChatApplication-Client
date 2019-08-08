# ChatApplication Client

This is a chat client application built using JavaFx and Java Nio.
The Server application for this client can be found [here](https://github.com/nifey/ChatApplication-Server).

![Screenshot](https://github.com/nifey/ChatApplication-Client/blob/master/Screenshot.png)

## Slash commands

It makes use of slash commands for functionality like creating groups, adding members, etc.

### Available slash commands:
* \login username

Tries to login with the given username. You must login before you send any messages.

* \gcreate groupname

Tries to create a group with the given groupname and adds you as a member.

* \gadd groupname comma,separated,list,of,users

Adds the users given by the list of users to the group given by the groupname

* \gdelete groupname

Deletes the group if you are the admin of the group

* \gremove groupname comma,separated,list,of,users

Deletes the users given by the list of users from the group given by groupname. You can remove users only if you are an admin or if you just want to remove yourself from the group.

* \send path_to_file

Sends the file specified by the path to the user or group

* \logout

Logs you out. Don't forget to logout before closing the application.
