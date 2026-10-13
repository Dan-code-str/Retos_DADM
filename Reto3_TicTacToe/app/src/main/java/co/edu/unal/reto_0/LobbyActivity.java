package co.edu.unal.reto_0;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;

public class LobbyActivity extends AppCompatActivity {

    private Button btnCreateGame;
    private ListView lvGames;
    private ArrayList<String> gamesList;
    private ArrayAdapter<String> adapter;

    // Variables de Firebase
    private FirebaseDatabase database;
    private DatabaseReference gamesRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lobby);

        // 1. Vincular los elementos de la pantalla
        btnCreateGame = findViewById(R.id.btnCreateGame);
        lvGames = findViewById(R.id.lvGames);

        // 2. Configurar la lista visual
        gamesList = new ArrayList<>();
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, gamesList);
        lvGames.setAdapter(adapter);

        // 3. Inicializar Firebase
        database = FirebaseDatabase.getInstance();
        gamesRef = database.getReference("games");

        // 4. Lógica para el botón "Crear Nueva Partida"[cite: 9]
        btnCreateGame.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String gameId = gamesRef.push().getKey(); // Crea un ID único
                if (gameId != null) {
                    gamesRef.child(gameId).child("status").setValue("waiting");

                    // Viajar a la pantalla del juego
                    Intent intent = new Intent(LobbyActivity.this, AndroidTicTacToeActivity.class);
                    intent.putExtra("gameId", gameId);
                    intent.putExtra("player", "X"); // El que crea el juego es la "X"
                    startActivity(intent);
                }
            }
        });

        // 5. Lógica para escuchar las partidas disponibles en tiempo real[cite: 9]
        gamesRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                gamesList.clear(); // Limpiamos la lista para no duplicar
                for (DataSnapshot gameSnapshot : snapshot.getChildren()) {
                    String status = gameSnapshot.child("status").getValue(String.class);
                    // Solo mostramos los juegos que estén esperando un jugador[cite: 9]
                    if ("waiting".equals(status)) {
                        gamesList.add(gameSnapshot.getKey());
                    }
                }
                adapter.notifyDataSetChanged(); // Actualizamos la pantalla
            }

            @Override
            public void onCancelled(DatabaseError error) { }
        });

        // 6. Lógica para unirse a un juego al tocarlo en la lista[cite: 9]
        lvGames.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                String gameId = gamesList.get(position);
                gamesRef.child(gameId).child("status").setValue("playing");

                // Viajar a la pantalla del juego
                Intent intent = new Intent(LobbyActivity.this, AndroidTicTacToeActivity.class);
                intent.putExtra("gameId", gameId);
                intent.putExtra("player", "O"); // El que se une es la "O"
                startActivity(intent);
            }
        });
    }
}