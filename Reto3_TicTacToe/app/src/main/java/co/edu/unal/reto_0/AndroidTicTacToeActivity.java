package co.edu.unal.reto_0;

import android.os.Handler;
import android.media.MediaPlayer;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent; // Importación necesaria para los toques
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import android.content.Context;
import android.view.LayoutInflater;
import android.content.SharedPreferences; // Asegúrate de agregar la importación


public class AndroidTicTacToeActivity extends AppCompatActivity {

    private TicTacToeGame mGame;
    private BoardView mBoardView; // NUEVO: Variable para el tablero visual
    private MediaPlayer mHumanMediaPlayer;
    private MediaPlayer mComputerMediaPlayer;
    private TextView mInfoTextView;
    private boolean mGameOver;
    private SharedPreferences mPrefs;

    private int mHumanWins = 0;
    private int mComputerWins = 0;
    private int mTies = 0;
    private boolean mHumanFirst = true;
    private char mTurn = TicTacToeGame.HUMAN_PLAYER;

    private TextView mHumanScoreTextView;
    private TextView mTieScoreTextView;
    private TextView mAndroidScoreTextView;

    static final int DIALOG_DIFFICULTY_ID = 0;
    static final int DIALOG_QUIT_ID = 1;
    static final int DIALOG_ABOUT_ID = 2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //getSupportActionBar().hide();
        setContentView(R.layout.activity_main);

        mInfoTextView = (TextView) findViewById(R.id.information);
        mHumanScoreTextView = (TextView) findViewById(R.id.human_score);
        mTieScoreTextView = (TextView) findViewById(R.id.tie_score);
        mAndroidScoreTextView = (TextView) findViewById(R.id.android_score);

        mGame = new TicTacToeGame();
        // Restaurar las preferencias (Puntajes y Dificultad)
        mPrefs = getSharedPreferences("ttt_prefs", MODE_PRIVATE);
        mHumanWins = mPrefs.getInt("mHumanWins", 0);
        mComputerWins = mPrefs.getInt("mComputerWins", 0);
        mTies = mPrefs.getInt("mTies", 0);

        // Reto extra: Restaurar la dificultad (usando números enteros: 0=Fácil, 1=Difícil, 2=Experto)
        int difficulty = mPrefs.getInt("mDifficulty", 2);
        if (difficulty == 0) mGame.setDifficultyLevel(TicTacToeGame.DifficultyLevel.Easy);
        else if (difficulty == 1) mGame.setDifficultyLevel(TicTacToeGame.DifficultyLevel.Harder);
        else mGame.setDifficultyLevel(TicTacToeGame.DifficultyLevel.Expert);

        displayScores();

        // NUEVO: Instanciar el tablero visual y asignar el juego[cite: 7]
        mBoardView = (BoardView) findViewById(R.id.board);
        mBoardView.setGame(mGame);

        // NUEVO: Escuchar los toques en la pantalla[cite: 7]
        mBoardView.setOnTouchListener(mTouchListener);

        startNewGame();
    }

    private void checkWinner(int winner) {
        if (winner == 1) {
            mInfoTextView.setText(R.string.result_tie);
            mTies++;
            mTieScoreTextView.setText("Ties: " + mTies);
            mGameOver = true;
        } else if (winner == 2) {
            mInfoTextView.setText(R.string.result_human_wins);
            mHumanWins++;
            mHumanScoreTextView.setText("Human: " + mHumanWins);
            mGameOver = true;
        } else if (winner == 3) {
            mInfoTextView.setText(R.string.result_computer_wins);
            mComputerWins++;
            mAndroidScoreTextView.setText("Android: " + mComputerWins);
            mGameOver = true;
        }
    }

    private void startNewGame() {
        mGame.clearBoard();
        mGameOver = false;
        mBoardView.invalidate();

        if (mHumanFirst) {
            mInfoTextView.setText(R.string.first_human);
            mTurn = TicTacToeGame.HUMAN_PLAYER;
            mHumanFirst = false;
        } else {
            mInfoTextView.setText(R.string.turn_computer);
            mTurn = TicTacToeGame.COMPUTER_PLAYER; // Bloquea toques humanos

            // Retraso de 1 segundo para el primer movimiento si empieza Android
            Handler handler = new Handler();
            handler.postDelayed(new Runnable() {
                public void run() {
                    try { // <-- PROTECCIÓN AQUÍ
                        int move = mGame.getComputerMove();
                        setMove(TicTacToeGame.COMPUTER_PLAYER, move);
                        mInfoTextView.setText(R.string.turn_human);
                        mTurn = TicTacToeGame.HUMAN_PLAYER;
                    } catch (Exception e) {
                        // Ignorar si la pantalla rotó
                    }
                }
            }, 1000);

            mHumanFirst = true;
        }
    }

    private View.OnTouchListener mTouchListener = new View.OnTouchListener() {
        public boolean onTouch(View v, MotionEvent event) {
            int col = (int) event.getX() / mBoardView.getBoardCellWidth();
            int row = (int) event.getY() / mBoardView.getBoardCellHeight();
            int pos = row * 3 + col;

            // NUEVO: Solo permite colocar ficha si es turno humano[cite: 7]
            if (!mGameOver && mTurn == TicTacToeGame.HUMAN_PLAYER && setMove(TicTacToeGame.HUMAN_PLAYER, pos)) {

                int winner = mGame.checkForWinner();
                if (winner == 0) {
                    mInfoTextView.setText(R.string.turn_computer);
                    mTurn = TicTacToeGame.COMPUTER_PLAYER; // Bloquea la pantalla[cite: 7]

                    // NUEVO: El Handler crea un retraso de 1 segundo antes de ejecutar el Runnable[cite: 7]
                    Handler handler = new Handler();
                    handler.postDelayed(new Runnable() {
                        public void run() {
                            try { // NUEVO: Bloque try para atrapar el error si la pantalla ya no existe
                                int move = mGame.getComputerMove();
                                setMove(TicTacToeGame.COMPUTER_PLAYER, move);

                                int winnerAfterComputer = mGame.checkForWinner();
                                if (winnerAfterComputer == 0) {
                                    mInfoTextView.setText(R.string.turn_human);
                                    mTurn = TicTacToeGame.HUMAN_PLAYER;
                                } else {
                                    checkWinner(winnerAfterComputer);
                                }
                            } catch (Exception e) {
                                // Se ignora el error porque la Activity vieja ya murió
                            }
                        }
                    }, 1000); // 1000 ms = 1 segundo de retraso[cite: 7]

                } else {
                    checkWinner(winner);
                }
            }
            return false;
        }
    };

    // NUEVO: Devuelve boolean e invalida el tablero para forzar el redibujado[cite: 7]
    private boolean setMove(char player, int location) {
        if (mGame.setMove(player, location)) {
            mBoardView.invalidate(); // Redibuja el tablero

            // Protección: Solo reproducir si los MediaPlayers no son nulos y atrapar errores
            try {
                if (player == TicTacToeGame.HUMAN_PLAYER && mHumanMediaPlayer != null) {
                    mHumanMediaPlayer.start();
                } else if (player == TicTacToeGame.COMPUTER_PLAYER && mComputerMediaPlayer != null) {
                    mComputerMediaPlayer.start();
                }
            } catch (Exception e) {
                // Si el audio falla al rotar, simplemente se ignora y el juego continúa
            }

            return true;
        }
        return false;
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.options_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.new_game) {
            startNewGame();
            return true;
        } else if (id == R.id.ai_difficulty) {
            showDialog(DIALOG_DIFFICULTY_ID);
            return true;
        } else if (id == R.id.reset_scores) {
            mHumanWins = 0;
            mComputerWins = 0;
            mTies = 0;
            displayScores();
            return true;
        } else if (id == R.id.about) {
            showDialog(DIALOG_ABOUT_ID);
            return true;
        }
        return false;
    }

    @Override
    protected Dialog onCreateDialog(int id) {
        Dialog dialog = null;
        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        switch(id) {
            case DIALOG_DIFFICULTY_ID:
                builder.setTitle(R.string.difficulty_choose);
                final CharSequence[] levels = {
                        getResources().getString(R.string.difficulty_easy),
                        getResources().getString(R.string.difficulty_harder),
                        getResources().getString(R.string.difficulty_expert)};

                // TODO resuelto: Determinar qué nivel está seleccionado actualmente
                int selected = 2; // Por defecto Experto
                if (mGame.getDifficultyLevel() == TicTacToeGame.DifficultyLevel.Easy) selected = 0;
                else if (mGame.getDifficultyLevel() == TicTacToeGame.DifficultyLevel.Harder) selected = 1;

                builder.setSingleChoiceItems(levels, selected,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int item) {
                                dialog.dismiss(); // Close dialog

                                // TODO resuelto: Cambiar la dificultad del juego según la selección
                                if (item == 0) mGame.setDifficultyLevel(TicTacToeGame.DifficultyLevel.Easy);
                                else if (item == 1) mGame.setDifficultyLevel(TicTacToeGame.DifficultyLevel.Harder);
                                else mGame.setDifficultyLevel(TicTacToeGame.DifficultyLevel.Expert);

                                // Display the selected difficulty level
                                Toast.makeText(getApplicationContext(), levels[item], Toast.LENGTH_SHORT).show();
                            }
                        });
                dialog = builder.create();
                break;

            case DIALOG_QUIT_ID:
                // Create the quit confirmation dialog
                builder.setMessage(R.string.quit_question)
                        .setCancelable(false)
                        .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                AndroidTicTacToeActivity.this.finish();
                            }
                        })
                        .setNegativeButton(R.string.no, null);
                dialog = builder.create();
                break;

            case DIALOG_ABOUT_ID:
                Context context = getApplicationContext();
                LayoutInflater inflater = (LayoutInflater) context.getSystemService(LAYOUT_INFLATER_SERVICE);
                View layout = inflater.inflate(R.layout.about_dialog, null);
                builder.setView(layout);
                builder.setPositiveButton("OK", null);
                dialog = builder.create();
                break;
        }
        return dialog;
    }
    @Override
    protected void onResume() {
        super.onResume();
        // Cambia "sword" y "swish" por los nombres exactos de tus audios sin el .mp3
        mHumanMediaPlayer = MediaPlayer.create(getApplicationContext(), R.raw.fire);
        mComputerMediaPlayer = MediaPlayer.create(getApplicationContext(), R.raw.water);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mHumanMediaPlayer.release();
        mComputerMediaPlayer.release();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putCharArray("board", mGame.getBoardState());
        outState.putBoolean("mGameOver", mGameOver);
        outState.putInt("mHumanWins", Integer.valueOf(mHumanWins));
        outState.putInt("mComputerWins", Integer.valueOf(mComputerWins));
        outState.putInt("mTies", Integer.valueOf(mTies));
        outState.putCharSequence("info", mInfoTextView.getText());
        outState.putChar("mTurn", mTurn); // Guardamos de quién es el turno
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        mGame.setBoardState(savedInstanceState.getCharArray("board"));
        mGameOver = savedInstanceState.getBoolean("mGameOver");
        mInfoTextView.setText(savedInstanceState.getCharSequence("info"));
        mHumanWins = savedInstanceState.getInt("mHumanWins");
        mComputerWins = savedInstanceState.getInt("mComputerWins");
        mTies = savedInstanceState.getInt("mTies");
        mTurn = savedInstanceState.getChar("mTurn"); // Restauramos el turno

        displayScores();

        displayScores();

        // NUEVO: Si al rotar la pantalla era el turno de la computadora, debe tirar inmediatamente
        if (mTurn == TicTacToeGame.COMPUTER_PLAYER) {
            int move = mGame.getComputerMove();
            setMove(TicTacToeGame.COMPUTER_PLAYER, move);

            int winner = mGame.checkForWinner();
            if (winner == 0) {
                mInfoTextView.setText(R.string.turn_human);
                mTurn = TicTacToeGame.HUMAN_PLAYER;
            } else {
                checkWinner(winner);
            }
        }
    }

    // Método auxiliar para actualizar los textos de los puntajes[cite: 20]
    private void displayScores() {
        mHumanScoreTextView.setText("Human: " + mHumanWins);
        mAndroidScoreTextView.setText("Android: " + mComputerWins);
        mTieScoreTextView.setText("Ties: " + mTies);
    }

    @Override
    protected void onStop() {
        super.onStop();

        // Guardar los puntajes y la dificultad actuales
        SharedPreferences.Editor ed = mPrefs.edit();
        ed.putInt("mHumanWins", mHumanWins);
        ed.putInt("mComputerWins", mComputerWins);
        ed.putInt("mTies", mTies);

        // Reto extra: Guardar la dificultad convirtiendo el Enum a un entero
        int diff = 2; // Por defecto experto
        if (mGame.getDifficultyLevel() == TicTacToeGame.DifficultyLevel.Easy) diff = 0;
        else if (mGame.getDifficultyLevel() == TicTacToeGame.DifficultyLevel.Harder) diff = 1;
        ed.putInt("mDifficulty", diff);

        ed.commit(); // Guarda físicamente los datos en el teléfono
    }
}