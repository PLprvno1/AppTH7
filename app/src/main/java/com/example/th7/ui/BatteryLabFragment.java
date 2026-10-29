package com.example.th7.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

/** Empty compatibility fragment so saved Activity state from older app versions can be discarded safely. */
@Deprecated
public final class BatteryLabFragment extends Fragment {
    @Nullable
    @Override
    public View onCreateView(@androidx.annotation.NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return null;
    }
}
