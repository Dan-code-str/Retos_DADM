package co.edu.unal.reto_0;

import java.util.Random;

public class TicTacToeGame {

    private char mBoard[] = new char[9];
    public static final int BOARD_SIZE = 9;

    // Characters used to represent the human, computer, and open spots
    public static final char HUMAN_PLAYER = 'X';
    public static final char COMPUTER_PLAYER = 'O';
    public static final char OPEN_SPOT = ' ';

    private Random mRand;

    public TicTacToeGame() {
        // Seed the random number generator
        mRand = new Random();
    }

    /** Clear the board of all X's and O's by setting all spots to OPEN_SPOT. */
    public void clearBoard() {
        for (int i = 0; i < BOARD_SIZE; i++) {
            mBoard[i] = OPEN_SPOT;
        }
    }

    /** Set the given player at the given location on the game board. */
    public void setMove(char player, int location) {
        if (mBoard[location] == OPEN_SPOT) {
            mBoard[location] = player;
        }
    }

    /** Return the best move for the computer to make. You must call setMove()
     * to actually make the computer move to that location.
     * @return The best move for the computer to make (0-8).
     */
    public int getComputerMove() {
        int move = -1;

        // First see if there's a move O can make to win
        for (int i = 0; i < BOARD_SIZE; i++) {
            if (mBoard[i] == OPEN_SPOT) {
                char curr = mBoard[i];
                mBoard[i] = COMPUTER_PLAYER;
                if (checkForWinner() == 3) {
                    mBoard[i] = OPEN_SPOT; // Revertir el tablero a su estado original
                    return i;
                } else {
                    mBoard[i] = curr;
                }
            }
        }

        // See if there's a move O can make to block X from winning
        for (int i = 0; i < BOARD_SIZE; i++) {
            if (mBoard[i] == OPEN_SPOT) {
                char curr = mBoard[i];
                mBoard[i] = HUMAN_PLAYER;
                if (checkForWinner() == 2) {
                    mBoard[i] = OPEN_SPOT; // Revertir el tablero a su estado original
                    return i;
                } else {
                    mBoard[i] = curr;
                }
            }
        }

        // Generate random move
        do {
            move = mRand.nextInt(BOARD_SIZE);
        } while (mBoard[move] == HUMAN_PLAYER || mBoard[move] == COMPUTER_PLAYER);

        return move;
    }

    /**
     * Check for a winner and return a status value indicating who has won.
     * @return Return 0 if no winner or tie yet, 1 if it's a tie, 2 if X won, or 3 if O won.
     */
    public int checkForWinner() {
        // Check horizontal wins
        for (int i = 0; i <= 6; i += 3) {
            if (mBoard[i] == HUMAN_PLAYER && mBoard[i+1] == HUMAN_PLAYER && mBoard[i+2] == HUMAN_PLAYER)
                return 2;
            if (mBoard[i] == COMPUTER_PLAYER && mBoard[i+1] == COMPUTER_PLAYER && mBoard[i+2] == COMPUTER_PLAYER)
                return 3;
        }

        // Check vertical wins
        for (int i = 0; i <= 2; i++) {
            if (mBoard[i] == HUMAN_PLAYER && mBoard[i+3] == HUMAN_PLAYER && mBoard[i+6] == HUMAN_PLAYER)
                return 2;
            if (mBoard[i] == COMPUTER_PLAYER && mBoard[i+3] == COMPUTER_PLAYER && mBoard[i+6] == COMPUTER_PLAYER)
                return 3;
        }

        // Check for diagonal wins
        if ((mBoard[0] == HUMAN_PLAYER && mBoard[4] == HUMAN_PLAYER && mBoard[8] == HUMAN_PLAYER) ||
                (mBoard[2] == HUMAN_PLAYER && mBoard[4] == HUMAN_PLAYER && mBoard[6] == HUMAN_PLAYER))
            return 2;
        if ((mBoard[0] == COMPUTER_PLAYER && mBoard[4] == COMPUTER_PLAYER && mBoard[8] == COMPUTER_PLAYER) ||
                (mBoard[2] == COMPUTER_PLAYER && mBoard[4] == COMPUTER_PLAYER && mBoard[6] == COMPUTER_PLAYER))
            return 3;

        // Check for tie
        for (int i = 0; i < BOARD_SIZE; i++) {
            // If we find an empty spot, then no one has won yet
            if (mBoard[i] == OPEN_SPOT)
                return 0;
        }

        // If we make it through the previous loop, all places are taken, so it's a tie
        return 1;
    }
}