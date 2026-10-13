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
// Importaciones necesarias para Firebase
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;


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

    // Variables de Multijugador
    private String gameId;
    private String myPlayerSymbol; // "X" o "O"
    private String currentTurn = "X"; // Siempre empieza la "X"
    private DatabaseReference gameRef;

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
        // Suponiendo que tus variables se llaman así, diles que se oculten:

        mGame = new TicTacToeGame();

        // Recibir datos de la partida desde el Lobby
        gameId = getIntent().getStringExtra("gameId");
        myPlayerSymbol = getIntent().getStringExtra("player");

        if (gameId != null) {
            gameRef = FirebaseDatabase.getInstance().getReference("games").child(gameId);
            gameRef.onDisconnect().removeValue();

            // Si soy el creador ("X"), preparo un tablero inmaculado
            if (myPlayerSymbol.equals("X")) {
                // 1. Crear un arreglo de 9 casillas con el espacio vacío oficial
                char[] emptyBoard = new char[9];
                for (int i = 0; i < 9; i++) {
                    emptyBoard[i] = TicTacToeGame.OPEN_SPOT;
                }

                // 2. Limpiar el juego localmente
                mGame.setBoardState(emptyBoard);

                // 3. Subir el tablero limpio a Firebase
                gameRef.child("board").setValue(new String(emptyBoard));
                gameRef.child("turn").setValue("X");
            }

            listenForMoves();
        }

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

        //displayScores();

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
            //mTieScoreTextView.setText("Ties: " + mTies);
            mGameOver = true;
        } else if (winner == 2) {
            mInfoTextView.setText(R.string.result_human_wins);
            mHumanWins++;
            //mHumanScoreTextView.setText("Human: " + mHumanWins);
            mGameOver = true;
        } else if (winner == 3) {
            mInfoTextView.setText(R.string.result_computer_wins);
            mComputerWins++;
            //mAndroidScoreTextView.setText("Android: " + mComputerWins);
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
            if (event.getAction() != MotionEvent.ACTION_DOWN) return false;

            // 1. Si el juego ya terminó, bloqueamos cualquier toque nuevo
            if (mGameOver) {
                return false;
            }

            if (gameId == null) {
                return false;
            }

            int col = (int) event.getX() / mBoardView.getBoardCellWidth();
            int row = (int) event.getY() / mBoardView.getBoardCellHeight();
            int pos = row * 3 + col;

            if (!currentTurn.equals(myPlayerSymbol)) {
                android.widget.Toast.makeText(getApplicationContext(), "Espera tu turno", android.widget.Toast.LENGTH_SHORT).show();
                return false;
            }

            if (mGame.getBoardState()[pos] != TicTacToeGame.OPEN_SPOT) {
                return false;
            }

            // Hacer el movimiento localmente
            if (mGame.getBoardState()[pos] != TicTacToeGame.OPEN_SPOT) {
                return false;
            }

            // CORRECCIÓN: Usamos el símbolo correcto dependiendo de si eres el Jugador 1 o 2
            char playerChar = myPlayerSymbol.equals("X") ? TicTacToeGame.HUMAN_PLAYER : TicTacToeGame.COMPUTER_PLAYER;

            if (setMove(playerChar, pos)) {
                // NUEVO: Si eres X suena fuego (humano), si eres O suena agua (computadora)
                try {
                    if (myPlayerSymbol.equals("X") && mHumanMediaPlayer != null) {
                        mHumanMediaPlayer.start();
                    } else if (myPlayerSymbol.equals("O") && mComputerMediaPlayer != null) {
                        mComputerMediaPlayer.start();
                    }
                } catch (Exception e) {}
                String nextTurn = myPlayerSymbol.equals("X") ? "O" : "X";
                gameRef.child("board").setValue(new String(mGame.getBoardState()));
                gameRef.child("turn").setValue(nextTurn);
            }

            return true;
        }
    };

    // NUEVO: Devuelve boolean e invalida el tablero para forzar el redibujado[cite: 7]
    private boolean setMove(char player, int location) {
        if (mGame.getBoardState()[location] == TicTacToeGame.OPEN_SPOT) {
            mGame.setMove(player, location);
            mBoardView.invalidate();
            return true;
        }
        return false;
    }


    @Override
    public boolean onCreateOptionsMenu(android.view.Menu menu) {
        super.onCreateOptionsMenu(menu);
        // Creamos una única opción limpia
        menu.add(0, 1, 0, "Volver al Lobby");
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(android.view.MenuItem item) {
        if (item.getItemId() == 1) {
            // "finish()" cierra el Activity actual (el tablero),
            // te devuelve al Lobby y dispara el "onDestroy" que borra la base de datos.
            finish();
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

        //displayScores();

        //displayScores();

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
    /*private void displayScores() {
        mHumanScoreTextView.setText("Human: " + mHumanWins);
        mAndroidScoreTextView.setText("Android: " + mComputerWins);
        mTieScoreTextView.setText("Ties: " + mTies);
    }*/

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

    private void listenForMoves() {
        gameRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    String boardCloud = snapshot.child("board").getValue(String.class);
                    if (boardCloud != null) {

                        // Si el tablero que llega de la nube es distinto al local, el rival tiró
                        String localBoard = new String(mGame.getBoardState());
                        if (!localBoard.equals(boardCloud)) {
                            try {
                                // Si yo soy X, el rival es O (Agua). Si yo soy O, el rival es X (Fuego).
                                if (myPlayerSymbol.equals("X") && mComputerMediaPlayer != null) {
                                    mComputerMediaPlayer.start();
                                } else if (myPlayerSymbol.equals("O") && mHumanMediaPlayer != null) {
                                    mHumanMediaPlayer.start();
                                }
                            } catch (Exception e) {}
                        }

                        mGame.setBoardState(boardCloud.toCharArray());
                        mBoardView.invalidate();

                        // Revisar si alguien ganó
                        int winner = mGame.checkForWinner();
                        if (winner != 0) {
                            checkWinner(winner); // Dejamos esto por si tu app reproduce un sonido de fin de juego
                            mGameOver = true;

                            // NUEVO: Corregir el mensaje dependiendo de tu símbolo
                            if (winner == 1) {
                                mInfoTextView.setText("¡Es un empate!");
                            } else if (winner == 2) {
                                // El código original dice que 2 es victoria de la 'X'
                                if (myPlayerSymbol.equals("X")) {
                                    mInfoTextView.setText("¡Ganaste!");
                                } else {
                                    mInfoTextView.setText("El oponente ganó...");
                                }
                            } else if (winner == 3) {
                                // El código original dice que 3 es victoria de la 'O'
                                if (myPlayerSymbol.equals("O")) {
                                    mInfoTextView.setText("¡Ganaste!");
                                } else {
                                    mInfoTextView.setText("El oponente ganó...");
                                }
                            }
                        } else {
                            String turnCloud = snapshot.child("turn").getValue(String.class);
                            if (turnCloud != null) {
                                currentTurn = turnCloud;
                                if (currentTurn.equals(myPlayerSymbol)) {
                                    mInfoTextView.setText("Es tu turno (" + myPlayerSymbol + ")");
                                } else {
                                    mInfoTextView.setText("Turno del oponente...");
                                }
                            }
                        }
                    }
                }
            }
            @Override
            public void onCancelled(DatabaseError error) {}
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Si estábamos en una partida online, la borramos al salir
        if (gameId != null && gameRef != null) {
            gameRef.removeValue();
        }
    }
}