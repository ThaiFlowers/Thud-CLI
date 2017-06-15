package thud;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class Main {

	private enum SpecialActions {
	    NORMAL, SAVE, QUIT, FORFEIT;
	}

	private static Board board = new Board();
	private static BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
	private static List<List<String>> moveLogs = new ArrayList<>();
	private static int[] playerScores = new int[2]; // use mod 2 arithmetic to access index while scoring
	private static PlayState turn = new PlayState();
	private static SpecialActions specialAction;

    public static void main(String[] args) throws IOException {

    	boolean resumeRound = false;
    	int startRound;
    	if (args.length == 1) {
    	    System.out.println("Loading save file, if the game is complete it will be re-scored, if it is incomplete it will resume");
    	    try {
				resumeRound = loadFile(args[0]);
			}
			catch (FileNotFoundException ex) {
    	    	System.out.printf("File %s not found!", args[0]);
    	    	System.exit(0);
			}

    	    // stopped in middle of first round
    	    if (resumeRound && moveLogs.size() == 1) {
				turn = board.replayMoveLog(moveLogs.get(0));
			}
			// stopped at beginning of second round
            else if (!resumeRound && moveLogs.size() == 1) {
				board.replayMoveLog(moveLogs.get(0));
				turn.setTurn(BoardStates.DWARF);
			}
			// stopped in middle of second round
			else if (resumeRound && moveLogs.size() == 2) {
				board.replayMoveLog(moveLogs.get(0));
				turn = board.replayMoveLog(moveLogs.get(1));
			}
			// full game recovered
			else if (!resumeRound && moveLogs.size() == 2) {
    	        System.out.println("Full game recovered\n");
				board.replayMoveLog(moveLogs.get(0));
				calculateScores(1);
				board.replayMoveLog(moveLogs.get(1));
				calculateScores(2);
			}
			else {
				System.out.println("Shut er down Johnny, she's a pumpin mud!");
				System.out.println("moveLogs.size() " + moveLogs.size());
				System.out.println("resumeRound " + resumeRound);
				System.exit(-999);
			}

			startRound = moveLogs.size()-1;
		}
		else {
			startRound = 0;
		}

		for (int round=startRound; round < 2; round++) {

    	    // don't initialize a new round if we loaded from a file
    		if (!resumeRound) {
				board.initializeGame();

				System.out.printf("Starting round %d\n", round+1);
                System.out.println("Dwarfs move first");
			}
			else {
				System.out.printf("Resuming round %d\n", round + 1);
				System.out.printf("Current turn: %s\n",
					(turn.getTurn().equals(BoardStates.DWARF)) ? "Dwarfs" : "Trolls");
			}
			resumeRound = false;

			boolean playing = true;
			while (playing) {
				System.out.print(board);

				specialAction = playNext(turn);
				switch (specialAction) {
					case NORMAL:
						break;
					case QUIT:
						// don't allow saving if first round and empty moveLog
					    if ( !(moveLogs.size() == 0 && round == 0) ) {
							savePrompt();
						}
						System.exit(0);
						break;
					case SAVE:
                        if ( (moveLogs.size() == 0 && round == 0) ) {
                           System.out.println("Nothing to save!");
                        }
                        else {
							System.out.print("Filename: ");
							String input = in.readLine();
							saveFile(input);
						}
						break;
					case FORFEIT:
						playing = false;
						break;
				}
			}

			// store scores for the round
            calculateScores(round);
		}

		// determine final score and winner
		System.out.println();
		for (int i=0; i<2; i++) {
			System.out.printf("Player %d: %d\n", i+1, playerScores[i]);
		}

		if (playerScores[0] != playerScores[1]) {
			System.out.printf("\nPlayer %d Wins\n", (playerScores[0] > playerScores[1]) ? 1 : 2);
			System.out.printf("By a margin of %d points\n", Math.abs(playerScores[0] - playerScores[1]));
		}
		else
			System.out.println("Game was a draw");

		savePrompt();
	}

	// the boolean refers to whether we are in the middle of a round or not
	private static boolean loadFile(String fileName) throws IOException {
        // initialize or clear current moveLogs
        moveLogs = new ArrayList<>();

		boolean middleOfRound = false;
		List<String> roundMoveLog;

    	try (BufferedReader input = new BufferedReader(new FileReader(fileName))) {

    		for (int round=0; round<2; round++) {
				roundMoveLog = new ArrayList<>();
				String currentLine;

				// do-while b/c we need to check at least one command in first round
                // if first round ended due to null, this will be null and we will return the proper middleOfRound status
				currentLine = input.readLine();
				if (currentLine==null) {
					// this if makes it so first round is not added if empty file,
					// but second round is added if it is empty
					if (round!=0)
						moveLogs.add(roundMoveLog);
                    break;
				}
				do {
					// we have found a blank line, move on to next round
					if (currentLine.length()==0) {
						middleOfRound = false;
						break;
					}
					else {
						roundMoveLog.add(currentLine);
						middleOfRound = true;
					}
				} while ((currentLine = input.readLine()) != null);

				// This if makes it so first line is only added if not empty
					moveLogs.add(roundMoveLog);
			}
		}
		return middleOfRound;
	}

	private static void calculateScores(int round) {
		playerScores[round % 2] = board.getNumDwarfs() * 10;
		playerScores[(round+1) % 2] = board.getNumTrolls() * 40;
	}

	private static SpecialActions playNext(PlayState turn) throws IOException {
		boolean validMove = false;
		while (!validMove) {
			if (board.getNumDwarfs() == 0 || board.getNumTrolls() == 0)
				return SpecialActions.FORFEIT;

			System.out.println();
			System.out.print((turn.getTurn().equals(BoardStates.DWARF)) ? "Dwarfs: " : "Trolls: ");
			String move = in.readLine();

			// 'H'url by troll must be followed by an 'R'emove of 1 dwarf or more
            // don't allow interface commands to run in this case
            // (only Troll can 'H' so this test is safe)
			if (board.getLastMove().charAt(0) != 'H') {
				if (move.equalsIgnoreCase("exit"))
					return SpecialActions.QUIT;
				if (move.equalsIgnoreCase("save")) {
					return SpecialActions.SAVE;
				}
				if (move.equalsIgnoreCase("forfeit")) {
					char lastCmd = board.getLastMove().charAt(0);
					if (lastCmd == 'S') {
						throw new IllegalArgumentException("Can't forfeit mid shove.");
                    }
                    // check if last move allows implicit remove of nothing, if so add it as explicit command and forfeit
					if (lastCmd == 'M' && turn.getTurn().equals(BoardStates.TROLL)) {
						int[] oldEndPos = board.notationToPosition(board.getLastMove().substring(5));
						if (board.adjacentToAny(BoardStates.DWARF, oldEndPos)) {
							board.play(turn, "R");
						}
					}
					return SpecialActions.FORFEIT;
				}
			}

			try {
				board.play(turn, move);
				validMove = true;
			} catch (IllegalArgumentException ex) {
				System.out.println(ex.getMessage());
			}
		}

		return SpecialActions.NORMAL;
	}

	// file format is round1_moves empty_line round2_moves
	private static void saveFile(String fileName) throws IOException {
		try (BufferedWriter out = new BufferedWriter(new FileWriter(fileName))) {
			for (List<String> log : moveLogs) {
				for (String move : log) {
					out.write(move);
					out.newLine();
				}
			}
		}
	}

	private static void savePrompt() throws IOException {
		boolean validInput = false;
		while (!validInput) {
			System.out.print("Save? [Y/N] ");
			String input = in.readLine();
			if (input.equalsIgnoreCase("Y")) {
				validInput = true;
				System.out.print("Filename: ");
				input = in.readLine();
				saveFile(input);
			} else if (input.equalsIgnoreCase("N")) {
				validInput = true;
			}
		}
	}
}