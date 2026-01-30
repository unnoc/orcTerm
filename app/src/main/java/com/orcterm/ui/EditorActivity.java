package com.orcterm.ui;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.Toast;
import android.app.AlertDialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.orcterm.R;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class EditorActivity extends AppCompatActivity {

    private EditText editor;
    private File file;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_editor);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        editor = findViewById(R.id.editor);
        prefs = getSharedPreferences("orcterm_prefs", Context.MODE_PRIVATE);

        String path = getIntent().getStringExtra("path");
        if (path == null) {
            finish();
            return;
        }

        file = new File(path);
        setTitle(file.getName());

        loadSettings();
        loadFile();
    }

    private void loadSettings() {
        int fontSizeIdx = prefs.getInt("file_editor_font_size", 1);
        float size = 14f;
        switch (fontSizeIdx) {
            case 0: size = 12f; break;
            case 1: size = 14f; break;
            case 2: size = 18f; break;
            case 3: size = 24f; break;
        }
        editor.setTextSize(size);
    }

    private void loadFile() {
        new Thread(() -> {
            StringBuilder sb = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line).append("\n");
                }
            } catch (IOException e) {
                runOnUiThread(() -> Toast.makeText(this, "Error reading file: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
            runOnUiThread(() -> editor.setText(sb.toString()));
        }).start();
    }

    private void saveFile() {
        new Thread(() -> {
            try (FileWriter fw = new FileWriter(file)) {
                fw.write(editor.getText().toString());
                runOnUiThread(() -> {
                    Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show();
                    Intent result = new Intent();
                    result.putExtra("path", file.getAbsolutePath());
                    result.putExtra("remotePath", getIntent().getStringExtra("remotePath"));
                    setResult(RESULT_OK, result);
                    finish();
                });
            } catch (IOException e) {
                runOnUiThread(() -> Toast.makeText(this, "Error saving file: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void showFindDialog() {
        final EditText findInput = new EditText(this);
        findInput.setHint("Find...");
        
        new AlertDialog.Builder(this)
            .setTitle("Find")
            .setView(findInput)
            .setPositiveButton("Find Next", (d, w) -> find(findInput.getText().toString()))
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void find(String query) {
        if (query.isEmpty()) return;
        String content = editor.getText().toString();
        int index = content.indexOf(query, editor.getSelectionEnd());
        if (index == -1) index = content.indexOf(query); // Wrap around

        if (index != -1) {
            editor.setSelection(index, index + query.length());
            editor.requestFocus();
        } else {
            Toast.makeText(this, "Not found", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_editor, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            finish();
            return true;
        } else if (id == R.id.action_save) {
            saveFile();
            return true;
        } else if (id == R.id.action_find) {
            showFindDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}