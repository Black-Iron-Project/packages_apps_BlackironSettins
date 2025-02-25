/**
 * Copyright (C) 2022 BlackIron Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.blackiron.settings.fragments.ui;

import android.app.ActivityManager;
import android.annotation.Nullable;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.SectionIndexer;
import android.widget.Switch;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceFragment;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.android.settingslib.applications.ApplicationsState;

import com.android.settings.R;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.android.internal.util.blackiron.cutout.CutoutFullscreenController;

public class DisplayCutoutForceFullscreenFragment extends PreferenceFragment
        implements ApplicationsState.Callbacks {

    private ActivityManager mActivityManager;
    private AllPackagesAdapter mAllPackagesAdapter;
    private ApplicationsState mApplicationsState;
    private ApplicationsState.Session mSession;
    private ActivityFilter mActivityFilter;
    private PackageManager mPackageManager;
    private RecyclerView mAppsRecyclerView;

    private CutoutFullscreenController mCutoutForceFullscreenSettings;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Context context = getContext();
        mApplicationsState = ApplicationsState.getInstance(getActivity().getApplication());
        mSession = mApplicationsState.newSession(this);
        mSession.onResume();
        mPackageManager = context.getPackageManager();
        mActivityManager = (ActivityManager) context.getSystemService(
                Context.ACTIVITY_SERVICE);
        mActivityFilter = new ActivityFilter(mPackageManager);
        mAllPackagesAdapter = new AllPackagesAdapter(context);
        mCutoutForceFullscreenSettings = new CutoutFullscreenController(context);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        getActivity().getActionBar().setTitle(R.string.display_cutout_force_fullscreen_title);
        return inflater.inflate(R.layout.cutout_force_fullscreen_layout, container, false);
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {

    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
    }

    @Override
    public void onViewCreated(final View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mAppsRecyclerView = view.findViewById(R.id.user_list_view);
        mAppsRecyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));
        mAppsRecyclerView.setAdapter(mAllPackagesAdapter);
    }

    @Override
    public void onResume() {
        super.onResume();

        rebuild();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        mSession.onPause();
        mSession.onDestroy();
    }

    @Override
    public void onPackageListChanged() {
        mActivityFilter.updateLauncherInfoList();
        rebuild();
    }

    @Override
    public void onRebuildComplete(ArrayList<ApplicationsState.AppEntry> entries) {
        if (entries != null) {
            handleAppEntries(entries);
            mAllPackagesAdapter.notifyDataSetChanged();
        }
    }

    @Override
    public void onLoadEntriesCompleted() {
        rebuild();
    }

    @Override
    public void onAllSizesComputed() {}

    @Override
    public void onLauncherInfoChanged() {}

    @Override
    public void onPackageIconChanged() {}

    @Override
    public void onPackageSizeChanged(String packageName) {}

    @Override
    public void onRunningStateChanged(boolean running) {}

    private void handleAppEntries(List<ApplicationsState.AppEntry> entries) {
        final ArrayList<String> sections = new ArrayList<String>();
        final ArrayList<Integer> positions = new ArrayList<Integer>();
        String lastSectionIndex = null;
        int offset = 0;

        for (int i = 0; i < entries.size(); i++) {
            final ApplicationInfo info = entries.get(i).info;
            final String label = (String) info.loadLabel(mPackageManager);
            final String sectionIndex;

            if (!info.enabled) {
                sectionIndex = "--"; // XXX
            } else if (TextUtils.isEmpty(label)) {
                sectionIndex = "";
            } else {
                sectionIndex = label.substring(0, 1).toUpperCase();
            }

            if (lastSectionIndex == null ||
                    !TextUtils.equals(sectionIndex, lastSectionIndex)) {
                sections.add(sectionIndex);
                positions.add(offset);
                lastSectionIndex = sectionIndex;
            }

            offset++;
        }

        mAllPackagesAdapter.setEntries(entries, sections, positions);
    }

    private void rebuild() {
        mSession.rebuild(mActivityFilter, ApplicationsState.ALPHA_COMPARATOR);
    }

    private class AllPackagesAdapter extends RecyclerView.Adapter<ViewHolder>
            implements SectionIndexer {

        private List<ApplicationsState.AppEntry> mEntries = new ArrayList<>();
        private String[] mSections;
        private int[] mPositions;

        public AllPackagesAdapter(Context context) {
            if (mActivityFilter == null) {
                mActivityFilter = new ActivityFilter(mPackageManager);
            }
        }

        @Override
        public int getItemCount() {
            return mEntries.size();
        }

        @Override
        public long getItemId(int position) {
            return mEntries.get(position).id;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ViewHolder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.cutout_force_fullscreen_list_item, parent, false));
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            ApplicationsState.AppEntry entry = mEntries.get(position);

            if (entry == null) {
                return;
            }

            holder.title.setText(entry.label);
            holder.title.setOnClickListener(v -> holder.state.performClick());
            mApplicationsState.ensureIcon(entry);
            holder.icon.setImageDrawable(entry.info.loadIcon(mPackageManager));
            holder.state.setTag(entry);
            holder.state.setChecked(mCutoutForceFullscreenSettings.shouldForceCutoutFullscreen(entry.info.packageName));
            holder.state.setOnCheckedChangeListener((buttonView, isChecked) -> {
                final ApplicationsState.AppEntry appEntry =
                        (ApplicationsState.AppEntry) buttonView.getTag();

                if (isChecked) {
                    mCutoutForceFullscreenSettings.addApp(appEntry.info.packageName);
                } else {
                    mCutoutForceFullscreenSettings.removeApp(appEntry.info.packageName);
                }
                try{
                    mActivityManager.forceStopPackage(appEntry.info.packageName);
                } catch (Exception ignored) {
                }
            });
        }

        private void setEntries(List<ApplicationsState.AppEntry> entries,
                List<String> sections, List<Integer> positions) {
            mEntries = entries;
            mSections = sections.toArray(new String[sections.size()]);
            mPositions = new int[positions.size()];
            for (int i = 0; i < positions.size(); i++) {
                mPositions[i] = positions.get(i);
            }
            notifyDataSetChanged();
        }

        @Override
        public int getPositionForSection(int section) {
            if (section < 0 || section >= mSections.length) {
                return -1;
            }

            return mPositions[section];
        }

        @Override
        public int getSectionForPosition(int position) {
            if (position < 0 || position >= getItemCount()) {
                return -1;
            }

            final int index = Arrays.binarySearch(mPositions, position);

            /*
             * Consider this example: section positions are 0, 3, 5; the supplied
             * position is 4. The section corresponding to position 4 starts at
             * position 3, so the expected return value is 1. Binary search will not
             * find 4 in the array and thus will return -insertPosition-1, i.e. -3.
             * To get from that number to the expected value of 1 we need to negate
             * and subtract 2.
             */
            return index >= 0 ? index : -index - 2;
        }

        @Override
        public Object[] getSections() {
            return mSections;
        }
    }

    private static class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView title;
        private final ImageView icon;
        private final Switch state;
        private final View rootView;

        private ViewHolder(View view) {
            super(view);
            this.title = view.findViewById(R.id.app_name);
            this.icon = view.findViewById(R.id.app_icon);
            this.state = view.findViewById(R.id.state);
            this.rootView = view;

            view.setTag(this);
        }
    }

    private class ActivityFilter implements ApplicationsState.AppFilter {

        private final PackageManager mPackageManager;
        private final List<String> mLauncherResolveInfoList = new ArrayList<String>();

        private ActivityFilter(PackageManager packageManager) {
            this.mPackageManager = packageManager;

            updateLauncherInfoList();
        }

        public void updateLauncherInfoList() {
            Intent i = new Intent(Intent.ACTION_MAIN);
            i.addCategory(Intent.CATEGORY_LAUNCHER);
            List<ResolveInfo> resolveInfoList = mPackageManager.queryIntentActivities(i, 0);

            synchronized (mLauncherResolveInfoList) {
                mLauncherResolveInfoList.clear();
                for (ResolveInfo ri : resolveInfoList) {
                    mLauncherResolveInfoList.add(ri.activityInfo.packageName);
                }
            }
        }

        @Override
        public void init() {}

        @Override
        public boolean filterApp(ApplicationsState.AppEntry entry) {
            boolean show = !mAllPackagesAdapter.mEntries.contains(entry.info.packageName);
            if (show) {
                synchronized (mLauncherResolveInfoList) {
                    show = mLauncherResolveInfoList.contains(entry.info.packageName) && !entry.info.isSystemApp();
                }
            }
            return show;
        }
    }
}
