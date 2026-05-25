# Trivia Game - Client/Server Java Application

This project implements a multiplayer trivia game using Java sockets for client-server communication and a MySQL database for user authentication and leaderboard storage.

## Features
- User registration and login
- Multiple choice questions across categories (Math, Software, Hardware, Random)
- Real-time scoring and leaderboard
- Server-managed game state and question progression
- Swing-based GUI for server administration and client interface

## Setup Instructions

### Prerequisites
- Java Development Kit (JDK) 8 or higher
- MySQL Server

### Database Setup
1. Start your MySQL server.
2. Execute the SQL script found in `src/sql_code.txt` to create the `userdb` database and the required tables (`users` and `leaderboard`).
   ```bash
   mysql -u root -p < src/sql_code.txt
   ```

### Building and Running
1. Clone or download this repository.
2. Open the project in your preferred IDE (IntelliJ IDEA, Eclipse, etc.) or compile from the command line.
3. Compile all Java source files in the `src` directory.
4. Start the server by running `GameServer` (or via `ServerFrame` for GUI).
5. Launch one or more client instances by running `ClientApp`.

### How to Play
1. On the client launcher, choose to **Login** or **Register**.
2. Once logged in, wait for the server to start the game.
3. Answer multiple-choice questions by selecting A, B, C, or D.
4. Scores are updated in real-time, and a leaderboard is displayed at the end of the game.

## Screenshots

1. **Server Host Screen** showing terminal/server interface:
   ![Server Host Screen](screenshots/server-host.png)

2. **Client Side** showing login/registration screen:
   ![Client Login Screen](screenshots/client-login.png)

3. **Both Server and Client** during a question section:
   ![Gameplay Screenshot](screenshots/gameplay.png)

4. **End Screen** showing final scores and leaderboard:
   ![End Score Screen](screenshots/end-scores.png)


## Notes
- The server uses port `12345` by default. Match with your own port.
- All question files are plain text with pipe-delimited values: `question|optionA|optionB|optionC|optionD|answerIndex`.
- The server stores user scores in the `leaderboard` table after each game.

## Future Improvements
- Externalize database credentials using a config file or environment variables.
- Add encryption for passwords in the database (currently stored in plaintext for simplicity).
- Implement a lobby system for players to wait and chat before games start.
- Add more question categories and support for dynamic question loading.
- Enhance the GUI with better styling and responsiveness.
