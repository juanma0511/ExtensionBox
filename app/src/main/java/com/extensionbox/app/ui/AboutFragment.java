package com.extensionbox.app.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.extensionbox.app.BuildConfig;
import com.extensionbox.app.R;
import com.extensionbox.app.network.GitHubService;
import com.extensionbox.app.network.Release;
import com.extensionbox.app.network.UpdateChecker;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class AboutFragment extends Fragment {

    @Override
    public View onCreateView(LayoutInflater inf, ViewGroup parent, Bundle saved) {
        View v = inf.inflate(R.layout.fragment_about, parent, false);

        // Dynamic version from BuildConfig
        TextView tvVersion = v.findViewById(R.id.tvAboutVersion);
        if (tvVersion != null) {
            tvVersion.setText("v" + BuildConfig.VERSION_NAME);
        }

        // Dev card click listeners
        View devCard1 = v.findViewById(R.id.devCard1);
        if (devCard1 != null) {
            devCard1.setOnClickListener(view -> {
                // AI doesn't have a profile link, do nothing
            });
        }

        View devCard2 = v.findViewById(R.id.devCard2);
        if (devCard2 != null) {
            devCard2.setOnClickListener(view -> {
                try {
                    Intent i = new Intent(Intent.ACTION_VIEW,
                            Uri.parse("https://github.com/omersusin"));
                    startActivity(i);
                } catch (Exception ignored) {}
            });
        }

        // Source code link
        View btnSource = v.findViewById(R.id.btnSourceCode);
        if (btnSource != null) {
            btnSource.setOnClickListener(view -> {
                try {
                    Intent i = new Intent(Intent.ACTION_VIEW,
                            Uri.parse("https://github.com/omersusin/ExtensionBox"));
                    startActivity(i);
                } catch (Exception ignored) {}
            });
        }

        checkForUpdates();

        return v;
    }

    private void checkForUpdates() {
        GitHubService service = UpdateChecker.getGitHubService();
        Call<List<Release>> call = service.getReleases("omersusin", "ExtensionBox");

        call.enqueue(new Callback<List<Release>>() {
            @Override
            public void onResponse(Call<List<Release>> call, Response<List<Release>> response) {
                if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                    Release latestRelease = response.body().get(0);
                    String latestVersionName = latestRelease.getTagName();
                    String currentVersionName = BuildConfig.VERSION_NAME;

                    if (isNewerVersion(latestVersionName, currentVersionName)) {
                        showUpdateDialog(latestVersionName);
                    }
                }
            }

            @Override
            public void onFailure(Call<List<Release>> call, Throwable t) {
                // Handle failure
            }
        });
    }

    private boolean isNewerVersion(String latestVersion, String currentVersion) {
        String[] latestParts = latestVersion.replace("v", "").split("\\.");
        String[] currentParts = currentVersion.replace("v", "").split("\\.");
        int length = Math.max(latestParts.length, currentParts.length);
        for (int i = 0; i < length; i++) {
            int latestPart = i < latestParts.length ? Integer.parseInt(latestParts[i]) : 0;
            int currentPart = i < currentParts.length ? Integer.parseInt(currentParts[i]) : 0;
            if (latestPart > currentPart) {
                return true;
            }
            if (latestPart < currentPart) {
                return false;
            }
        }
        return false;
    }


    private void showUpdateDialog(String latestVersion) {
        new AlertDialog.Builder(getContext())
                .setTitle("Update Available")
                .setMessage("A new version (" + latestVersion + ") is available. Would you like to update?")
                .setPositiveButton("Update", (dialog, which) -> {
                    Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/omersusin/ExtensionBox/releases/latest"));
                    startActivity(browserIntent);
                })
                .setNegativeButton("Later", null)
                .show();
    }
}
