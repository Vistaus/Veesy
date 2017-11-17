package de.veesy.settings;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

import de.veesy.R;
import de.veesy.listview_util.ListItemCallback;
import de.veesy.listview_util.StraightListAdapter;

/**
 * Created by dfritsch on 17.11.2017.
 * veesy.de
 * hs-augsburg
 */

public class SettingsActivity extends Activity {
    private static List<String> DUMMY_DATA;
    static {
        DUMMY_DATA = new ArrayList<>();
        DUMMY_DATA.add("My Data");
        DUMMY_DATA.add("New Card");
        DUMMY_DATA.add("Bluetooth Settings");
        DUMMY_DATA.add("About Us");
    }
    private StraightListAdapter adapter;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.settings);

        initListView();
    }

    private void initListView() {
        ListView listView = findViewById(R.id.lVSettings);
        adapter = new StraightListAdapter(this, R.layout.straight_list_view_row, DUMMY_DATA);
        listView.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
        listView.setAdapter(adapter);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {

            }
        });
    }

    public void bShareClicked(View view) {
        finish();
    }
}