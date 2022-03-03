package ark.noah.audiosegmentv2.ui.home;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.graphics.BlendMode;
import android.graphics.BlendModeColorFilter;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.util.Pair;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import ark.noah.audiosegmentv2.R;
import ark.noah.audiosegmentv2.databinding.FragmentPlayerBinding;

public class PlayerFragment extends Fragment implements SegmentAdapter.SegmentAdapterToPlayerTransactionInterface {

    public static final String KEY_PLAYER_PREF = "";
    public static final String DEBUG_PLAYABLE_PREPARER_TAG = "=========Playable Preparer";

    private PlayerViewModel playerViewModel;
    private FragmentPlayerBinding binding;

    private Drawable icon_Play, icon_Pause, icon_Warning, icon_No_Image;
    private ViewGroup.LayoutParams recyclerMaxSizeParams;

    private MediaPlayer mediaPlayer;
    private Uri musicUri;
    private final static int SEEKMODE = MediaPlayer.SEEK_NEXT_SYNC;

    private int color_textSecondary;
    private boolean bInterruptDataModified, bInterruptNext, bInterruptPrev;

    private long lastCheckTime;

    private ExecutorService playablePreparerExecutor = Executors.newSingleThreadExecutor();

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        playerViewModel = new ViewModelProvider(this).get(PlayerViewModel.class);

        binding = FragmentPlayerBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        playablePreparerExecutor = Executors.newSingleThreadExecutor();

        //region Prepare visual resources;
        TypedValue value = new TypedValue();
        requireContext().getTheme().resolveAttribute(R.attr.TextColorDisabled, value, true);
        color_textSecondary = Color.valueOf(value.data).toArgb();
        ColorFilter secondaryColorFilter = new BlendModeColorFilter(color_textSecondary, BlendMode.SRC_ATOP);

        icon_Warning = ContextCompat.getDrawable(requireContext(), R.drawable.ic_baseline_error_24);
        icon_No_Image = ContextCompat.getDrawable(requireContext(), R.drawable.ic_baseline_image_not_supported_24);
        icon_Play = ContextCompat.getDrawable(requireContext(), R.drawable.ic_baseline_play_arrow_24);
        icon_Pause = ContextCompat.getDrawable(requireContext(), R.drawable.ic_baseline_pause_24);

        icon_Warning.setColorFilter(secondaryColorFilter);
        icon_No_Image.setColorFilter(secondaryColorFilter);
        //endregion

        //region Initial Setup - No file opened
        binding.txtPlayerSongtitle.setText(R.string.player_no_entry_short);
        binding.txtPlayerSongdesc.setText(R.string.player_no_entry_short);
        binding.imgPlayerAlbumart.setImageDrawable(icon_Warning);
        binding.btnAddnewmain.setVisibility(View.GONE);
        //endregion

        //region Recycler View resizing related code
        //set recycler view's size bound to nested scroll view, so that it can recycle
        binding.nestedscrollmain.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                recyclerMaxSizeParams = binding.recyclerViewSegments.getLayoutParams();
                recyclerMaxSizeParams.width = binding.nestedscrollmain.getWidth();
                recyclerMaxSizeParams.height = binding.nestedscrollmain.getHeight();
                resizeRecyclerToMax();

                binding.nestedscrollmain.getViewTreeObserver().removeOnGlobalLayoutListener(this);
            }
        });

        //force scroll parent scrollview when 'add new' button is visible
        Rect scrollBounds = new Rect();
        binding.nestedscrollmain.getHitRect(scrollBounds);
        binding.recyclerViewSegments.setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
            int scrolled = scrollY - oldScrollY;
            if(scrolled < 0) {
                if (binding.btnAddnewmain.getLocalVisibleRect(scrollBounds)) { //visible anyhow
                    binding.nestedscrollmain.smoothScrollBy(0, scrolled);
                }
            }
        });
        //endregion

        //region Open File Callback
        ActivityResultLauncher<Intent> fileActivityResultLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        Intent data = result.getData();
                        if (data != null) {
                            musicUri = data.getData();

                            //prepare getting metadata from audiofile
                            MediaMetadataRetriever metaRetriever = new MediaMetadataRetriever();
                            metaRetriever.setDataSource(requireContext(), musicUri);

                            //make player ready
                            mediaPlayer = MediaPlayer.create(requireContext(), musicUri);
                            binding.txtPlayerSongtitle.setText(metaRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE));
                            binding.txtPlayerSongdesc.setText(metaRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM));
                            ((MaterialButton) binding.btnAllloop).setIconTint(ContextCompat.getColorStateList(requireContext(), R.color.icon_color_inactive));
                            byte[] art = metaRetriever.getEmbeddedPicture();
                            if(art != null && art.length > 0) {
                                binding.imgPlayerAlbumart.setImageBitmap(BitmapFactory.decodeByteArray(art, 0, art.length));
                            } else {
                                binding.imgPlayerAlbumart.setImageDrawable(icon_No_Image);
                            }

                            //load data to recyclerview item
                            ArrayList<SegmentContainer> list = new ArrayList<>();
                            SegmentContainer content = new SegmentContainer();
                            content.setDescription(getResources().getString(R.string.txt_default_segment_name));
                            content.setStart_timestamp(0);
                            content.setCondition(SegmentContainer.CONDITION_ONOFF);
                            content.setEnd_timestamp(Long.parseLong(metaRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)));
                            list.add(content);

                            //initialize recyclerview
                            SegmentAdapter adapter = new SegmentAdapter(list);
                            adapter.setMyInterface(this);
                            binding.recyclerViewSegments.setLayoutManager(new LinearLayoutManager(requireContext()));
                            binding.recyclerViewSegments.setAdapter(adapter);

                            bInterruptDataModified = true;

                            //set recycler height matching to itemcount (as there will be single entry)
                            resizeRecyclerToMatching();

                            //hide empty list helpers
                            binding.btnOpenfile.setVisibility(View.GONE);
                            binding.txtNoaudio.setVisibility(View.GONE);

                            //show
                            binding.btnAddnewmain.setVisibility(View.VISIBLE);
                        }
                    }
                });
        //endregion

        //region Open File Button Listener
        binding.btnOpenfile.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("audio/*");
            intent.setFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            fileActivityResultLauncher.launch(intent);
        });
        //endregion

        //region Player Control Buttons Listener
        binding.btnPlay.setOnClickListener(v -> {
            while(true) {
                if(mediaPlayer!=null) {
                    if(mediaPlayer.isPlaying()) {
                        ((MaterialButton) binding.btnPlay).setIcon(icon_Play);
                        mediaPlayer.pause();
                    } else {
                        ((MaterialButton) binding.btnPlay).setIcon(icon_Pause);
                        mediaPlayer.start();
                    }
                    break;
                } else if(musicUri != null) {
//                    mediaPlayer = MediaPlayer.create(requireContext(), musicUri);
                    try {
                        mediaPlayer.setDataSource(musicUri.getPath());
                    } catch (IOException e) {
                        Toast.makeText(requireContext(), "Setting of media player failed", Toast.LENGTH_SHORT).show();
                    }

                    SegmentAdapter adapter = ((SegmentAdapter) binding.recyclerViewSegments.getAdapter());
                    ArrayList<SegmentContainer> containers = Objects.requireNonNull(adapter).getmData();
                    mediaPlayer.seekTo(
                            containers.get(getEarliestNextPlayableIndex(containers, 0)).getStart_timestamp(),
                            SEEKMODE
                    );
                } else {
                    Toast.makeText(requireContext(), R.string.player_no_entry, Toast.LENGTH_SHORT).show();
                    break;
                }
            }
        });

        binding.btnAllloop.setOnClickListener(v -> {
            if(mediaPlayer != null) {
                if(mediaPlayer.isLooping()) {
                    mediaPlayer.setLooping(false);
                    ((MaterialButton) binding.btnAllloop).setIconTint(ContextCompat.getColorStateList(requireContext(), R.color.icon_color_inactive));
                }
                else {
                    mediaPlayer.setLooping(true);
                    ((MaterialButton) binding.btnAllloop).setIconTint(ContextCompat.getColorStateList(requireContext(), R.color.icon_color_active));
                }
            }
        });

        binding.btnNext.setOnClickListener(v -> {
            if(!bInterruptNext) bInterruptNext = true;
        });
        binding.btnPrev.setOnClickListener(v -> {
            if(!bInterruptPrev) bInterruptPrev = true;
        });
        //endregion

        //region Debug Split
        binding.btnDebugSplit.setOnClickListener(v -> {
            SegmentAdapter adapter = ((SegmentAdapter) binding.recyclerViewSegments.getAdapter());
            try {
                if(adapter == null || adapter.isDatasetEmpty()) throw new Exception();
                else {
                    for(int i = 0; i < 11; ++i) {
                        Pair<SegmentContainer, SegmentContainer> pair = adapter.getItemAtPosition(i).split(SegmentContainer.formatTimeElementsToLong(0,0,i+1,0));
                        if(pair == null) throw new Exception();
                        adapter.updateItem(i, pair.first);
                        adapter.addNewDataAtPosition(i+1, pair.second);
                    }
                }
                binding.btnDebugSplit.setVisibility(View.GONE);
                bInterruptDataModified = true;

                resizeRecyclerView();
            } catch (Exception e) {
                Toast.makeText(requireContext(),"SOMETHING WENT WRONG" ,Toast.LENGTH_SHORT).show();
            }
        });
        //endregion

        //region Asynchronous Task
        playablePreparerExecutor.submit(() -> {
            Log.d(DEBUG_PLAYABLE_PREPARER_TAG, "Executor Launched");
            long nextPositionToBePlayed = -1;
            int nextToBePlayedIndex = -1;
            int currentPlaySegmentIndex = -1;
            ArrayList<SegmentContainer> containers = null;

            boolean bNext, bPrev, bAgain, bCheckCurrent, bFirstTimeOnly;
            bNext = bPrev = bAgain = bCheckCurrent = false;
            bFirstTimeOnly = true;

            boolean debugLoopEnter, debugMediaIsPlaying;
            debugLoopEnter = debugMediaIsPlaying = false;

            while (true) {
                try {
                    if(!debugLoopEnter) {
                        debugLoopEnter = true;
                        Log.d(DEBUG_PLAYABLE_PREPARER_TAG, "Loop Entered");
                    }
                    if (bInterruptDataModified) {
                        Log.d(DEBUG_PLAYABLE_PREPARER_TAG, "Interruption: Data Modified");
                        containers = ((SegmentAdapter) Objects.requireNonNull(binding.recyclerViewSegments.getAdapter())).getmData();
                        bInterruptDataModified = false;
                        nextToBePlayedIndex = -1;
                        nextPositionToBePlayed = -1;
                        bCheckCurrent = true;
                    }
                    if(bInterruptPrev) {
                        Log.d(DEBUG_PLAYABLE_PREPARER_TAG, "Interruption: To Previous Segment");
                        bInterruptPrev = false;
                        bPrev = true;
                    }
                    if(bInterruptNext) {
                        Log.d(DEBUG_PLAYABLE_PREPARER_TAG, "Interruption: To Next Segment");
                        bInterruptNext = false;
                        bNext = true;
                    }

                    //
                    if(containers != null) {
                        //
                        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                            if(!debugMediaIsPlaying) {
                                debugMediaIsPlaying = true;
                                Log.d(DEBUG_PLAYABLE_PREPARER_TAG, "MediaPlayer started playing audio");
                            }
                            if (bFirstTimeOnly) {
                                Log.d(DEBUG_PLAYABLE_PREPARER_TAG, "First Time Only");
                                if (currentPlaySegmentIndex == -1) {
                                    currentPlaySegmentIndex = getEarliestPlayableIndex(containers);
                                    Log.d(DEBUG_PLAYABLE_PREPARER_TAG, "Current play position's index is " + currentPlaySegmentIndex);
                                }
                                nextToBePlayedIndex = getEarliestNextPlayableIndex(containers, currentPlaySegmentIndex);
                                nextPositionToBePlayed = containers.get(nextToBePlayedIndex).getStart_timestamp();
                                Log.d(DEBUG_PLAYABLE_PREPARER_TAG, "Next play position is " + nextPositionToBePlayed);
                                Log.d(DEBUG_PLAYABLE_PREPARER_TAG, "Next play position's index is " + nextToBePlayedIndex);
                                bFirstTimeOnly = false;
                            }
                            if (currentPlaySegmentIndex != -1) {
                                long now = System.currentTimeMillis();
                                if (lastCheckTime == 0) lastCheckTime = now;
                                long refTime = containers.get(currentPlaySegmentIndex).getEnd_timestamp();
                                long diff = now - lastCheckTime;
                                Log.d(DEBUG_PLAYABLE_PREPARER_TAG, "vals: " + now + ", " + lastCheckTime + ", " + refTime + ", " + diff);
                                Log.d(DEBUG_PLAYABLE_PREPARER_TAG, "check range between: " + (refTime-diff) + " ~ " + refTime + ", with a value of: " + mediaPlayer.getCurrentPosition());

                                if (SegmentContainer.isInRange(refTime - diff, refTime, mediaPlayer.getCurrentPosition())) {
                                    Log.d(DEBUG_PLAYABLE_PREPARER_TAG, "Current segment's end position has been reached");
                                    if(containers.get(currentPlaySegmentIndex).isLooping()) {
                                        bAgain = true;
                                        Log.d(DEBUG_PLAYABLE_PREPARER_TAG, "Current segment's condition was set to loop");
                                    }
                                    else {
                                        bNext = true;
                                        Log.d(DEBUG_PLAYABLE_PREPARER_TAG, "Going to next segment");
                                    }
                                }
                                if(bCheckCurrent) {
                                    bCheckCurrent = false;
                                    Log.d(DEBUG_PLAYABLE_PREPARER_TAG, "Check if current segment has been deactivated");
                                    if(!containers.get(currentPlaySegmentIndex).isOn()) {
                                        Log.d(DEBUG_PLAYABLE_PREPARER_TAG, "Current segment has been deactivated");
                                        Log.d(DEBUG_PLAYABLE_PREPARER_TAG, "Last current segment index was " + currentPlaySegmentIndex);
                                        currentPlaySegmentIndex = getEarliestNextPlayableIndex(containers, currentPlaySegmentIndex);
                                        nextToBePlayedIndex = getEarliestNextPlayableIndex(containers, currentPlaySegmentIndex);
                                        nextPositionToBePlayed = containers.get(nextToBePlayedIndex).getStart_timestamp();
                                        Log.d(DEBUG_PLAYABLE_PREPARER_TAG, "New current segment index is " + currentPlaySegmentIndex);
                                        mediaPlayer.seekTo(containers.get(currentPlaySegmentIndex).getStart_timestamp(), SEEKMODE);
                                    }
                                }
                                if(bNext) {
                                    bNext = false;
                                    Log.d(DEBUG_PLAYABLE_PREPARER_TAG, "Last current segment index was " + currentPlaySegmentIndex);
                                    mediaPlayer.seekTo(nextPositionToBePlayed, SEEKMODE);
                                    currentPlaySegmentIndex = nextToBePlayedIndex;
                                    nextToBePlayedIndex = getEarliestNextPlayableIndex(containers, currentPlaySegmentIndex);
                                    if(nextToBePlayedIndex == -1) throw new IllegalStateException();
                                    nextPositionToBePlayed = containers.get(nextToBePlayedIndex).getStart_timestamp();
                                    Log.d(DEBUG_PLAYABLE_PREPARER_TAG, "New current segment index is " + currentPlaySegmentIndex);
                                } else if (bAgain) {
                                    bAgain = false;
                                    Log.d(DEBUG_PLAYABLE_PREPARER_TAG, "Looping index of " + currentPlaySegmentIndex);
                                    mediaPlayer.seekTo(containers.get(currentPlaySegmentIndex).getStart_timestamp(), SEEKMODE);
                                } else if (bPrev) {
                                    bPrev = false;
                                    Log.d(DEBUG_PLAYABLE_PREPARER_TAG, "Last current segment index was " + currentPlaySegmentIndex);
                                    nextToBePlayedIndex = currentPlaySegmentIndex;
                                    nextPositionToBePlayed = containers.get(nextToBePlayedIndex).getStart_timestamp();
                                    currentPlaySegmentIndex = getEarliestPrevPlayableIndex(containers, currentPlaySegmentIndex);
                                    if(currentPlaySegmentIndex == -1) throw new IllegalStateException();
                                    else {
                                        Log.d(DEBUG_PLAYABLE_PREPARER_TAG, "New current segment index is " + currentPlaySegmentIndex);
                                        mediaPlayer.seekTo(containers.get(currentPlaySegmentIndex).getStart_timestamp(), SEEKMODE);
                                    }
                                }
                                lastCheckTime = now;
                            }
                        }
                    }
                } catch (IllegalStateException e) {
                    Log.d(DEBUG_PLAYABLE_PREPARER_TAG, "Something Went Wrong!!");
                    mediaPlayer.stop();
                    mediaPlayer.prepareAsync();
                    currentPlaySegmentIndex = -1;
                    nextToBePlayedIndex = -1;
                    nextPositionToBePlayed = -1;
                    lastCheckTime = 0;
                    bFirstTimeOnly = true;
                    requireActivity().runOnUiThread(()-> Toast.makeText(requireContext(), "Something Went Wrong!!", Toast.LENGTH_SHORT).show());
                }
            }
        });
        //endregion

        binding.recyclerViewSegments.addOnItemTouchListener(new RecyclerView.SimpleOnItemTouchListener() {
            @Override
            public void onTouchEvent(@NonNull RecyclerView rv, @NonNull MotionEvent e) {
                super.onTouchEvent(rv, e);
                bInterruptDataModified = true;
            }
        });

        if(mediaPlayer != null) mediaPlayer.setOnCompletionListener(mp -> ((MaterialButton) binding.btnPlay).setIcon(icon_Play));
        if(mediaPlayer != null) mediaPlayer.setOnErrorListener((mp, what, extra) -> {
            ((MaterialButton) binding.btnPlay).setIcon(icon_Play);
            return false;
        });

        return root;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
        playablePreparerExecutor.shutdown();
    }

    @Override
    public void openDialog(int position, SegmentContainer segmentContainer) {
        final Dialog rootDialog = new Dialog(requireContext());
        rootDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        rootDialog.setCancelable(true);
        rootDialog.setContentView(R.layout.dialog_longpress_segment);

        final TextView rename = rootDialog.findViewById(R.id.txt_rename);
        final TextView cut = rootDialog.findViewById(R.id.txt_cut);
        final TextView combine = rootDialog.findViewById(R.id.txt_combine);

        //region Rename
        rename.setOnClickListener(renameView -> {
            rootDialog.dismiss();
            final Dialog renameDialog = new Dialog(requireContext());
            renameDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
            renameDialog.setCancelable(true);
            renameDialog.setContentView(R.layout.dialog_rename_segment);

            final EditText typed = renameDialog.findViewById(R.id.eTxt_newname);
            final Button apply   = renameDialog.findViewById(R.id.btn_rename_apply);

            typed.setText(segmentContainer.getDescription());

            apply.setOnClickListener(applyView -> AsyncTask.execute(() -> {
                String newName = typed.getText().toString();
                if (newName.equals("")) {
                    requireActivity().runOnUiThread(() -> Toast.makeText(requireContext(), R.string.error_name, Toast.LENGTH_SHORT).show());
                } else {
                    segmentContainer.setDescription(newName);

                    renameDialog.dismiss();

                    requireActivity().runOnUiThread(() -> Objects.requireNonNull(binding.recyclerViewSegments.getAdapter()).notifyItemChanged(position));
                }
            }));
            renameDialog.show();
        });
        //endregion

        //region cut
        cut.setOnClickListener(cutView -> {
            rootDialog.dismiss();
            final Dialog cutDialog = new Dialog(requireContext());
            cutDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
            cutDialog.setCancelable(true);
            cutDialog.setContentView(R.layout.dialog_cut_segment2);

            final EditText hr     = cutDialog.findViewById(R.id.eTxt_hr);
            final EditText min    = cutDialog.findViewById(R.id.eTxt_min);
            final EditText sec    = cutDialog.findViewById(R.id.eTxt_secs);
            final EditText millis = cutDialog.findViewById(R.id.eTxt_millis);
            final Button apply    = cutDialog.findViewById(R.id.btn_cut2_apply);

            apply.setOnClickListener(applyView -> AsyncTask.execute(() -> {
                Pair<SegmentContainer, SegmentContainer> containers = segmentContainer.split(
                        SegmentContainer.formatTimeElementsToLong(
                                Integer.parseInt(hr    .getText().toString().equals("") ? "0" : hr    .getText().toString()),
                                Integer.parseInt(min   .getText().toString().equals("") ? "0" : min   .getText().toString()),
                                Integer.parseInt(sec   .getText().toString().equals("") ? "0" : sec   .getText().toString()),
                                Integer.parseInt(millis.getText().toString().equals("") ? "0" : millis.getText().toString())
                        )
                );

                if(containers == null) {
                    requireActivity().runOnUiThread(() -> Toast.makeText(requireContext(), "Given time is invalid!", Toast.LENGTH_SHORT).show());
                    return;
                }

                SegmentAdapter adapter = (SegmentAdapter) binding.recyclerViewSegments.getAdapter();

                try {
                    if(adapter == null) throw new Exception();

                    cutDialog.dismiss();
                    requireActivity().runOnUiThread(() -> {
                        adapter.updateItem(position, containers.first);
                        adapter.addNewDataAtPosition(position+1, containers.second);
                        resizeRecyclerView();
                    });
                } catch (Exception e) {
                    Toast.makeText(requireContext(),"SOMETHING WENT WRONG" ,Toast.LENGTH_SHORT).show();
                }
            }));
            cutDialog.show();
        });
        //endregion

        //region combine
        combine.setOnClickListener(combineView -> {
            rootDialog.dismiss();
            final Dialog mergeDialog = new Dialog(requireContext());
            mergeDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
            mergeDialog.setCancelable(true);
            mergeDialog.setContentView(R.layout.dialog_combine_segments);

            final TextView toPrev = mergeDialog.findViewById(R.id.txt_mergeToPrevious);
            final TextView toNext = mergeDialog.findViewById(R.id.txt_mergeToNext);

            toPrev.setOnClickListener(toPrevView -> AsyncTask.execute(() -> {
                if(position == 0) {
                    requireActivity().runOnUiThread(()-> Toast.makeText(requireContext(), requireContext().getText(R.string.txt_nothing_before),Toast.LENGTH_SHORT).show());
                } else {
                    SegmentAdapter adapter = (SegmentAdapter) binding.recyclerViewSegments.getAdapter();
                    try {
                        if(adapter == null) throw new Exception();

                        SegmentContainer prev = adapter.findMDataAtPosition(position-1);
                        SegmentContainer curr = adapter.findMDataAtPosition(position);
                        if(curr.isConsequentOrAdjacent(prev)) {
                            curr.mergeTargetToSelf(prev);
                            requireActivity().runOnUiThread(()-> {
                                adapter.updateItem(position, curr);
                                adapter.removeItem(position-1);

                                resizeRecyclerView();
                            });
                            mergeDialog.dismiss();
                        } else {
                            requireActivity().runOnUiThread(()-> Toast.makeText(requireContext(), requireContext().getText(R.string.txt_not_consequent_or_overlap),Toast.LENGTH_SHORT).show());
                        }
                    } catch (Exception e) {
                        Toast.makeText(requireContext(),"SOMETHING WENT WRONG" ,Toast.LENGTH_SHORT).show();
                    }
                }
            }));
            toNext.setOnClickListener(toNextView -> AsyncTask.execute(() -> {
                if(position >= Objects.requireNonNull(binding.recyclerViewSegments.getAdapter()).getItemCount() - 1) {
                    requireActivity().runOnUiThread(()-> Toast.makeText(requireContext(), requireContext().getText(R.string.txt_nothing_after),Toast.LENGTH_SHORT).show());
                } else {
                    SegmentAdapter adapter = (SegmentAdapter) binding.recyclerViewSegments.getAdapter();
                    try {
                        if(adapter == null) throw new Exception();

                        SegmentContainer curr = adapter.findMDataAtPosition(position);
                        SegmentContainer next = adapter.findMDataAtPosition(position+1);
                        if(curr.isConsequentOrAdjacent(next)) {
                            curr.mergeTargetToSelf(next);
                            requireActivity().runOnUiThread(()-> {
                                adapter.updateItem(position, curr);
                                adapter.removeItem(position+1);

                                resizeRecyclerView();
                            });
                            mergeDialog.dismiss();
                        } else {
                            requireActivity().runOnUiThread(()-> Toast.makeText(requireContext(), requireContext().getText(R.string.txt_not_consequent_or_overlap),Toast.LENGTH_SHORT).show());
                        }
                    } catch (Exception e) {
                        Toast.makeText(requireContext(),"SOMETHING WENT WRONG" ,Toast.LENGTH_SHORT).show();
                    }
                }
            }));
            mergeDialog.show();
        });
        //endregion

        rootDialog.show();
    }

    //region playlist control
    private int getEarliestPlayableIndex(ArrayList<SegmentContainer> data) {
        for(int i = 0; i < data.size(); ++i)
            if(data.get(i).isOn())
                return i;
        return -1;
    }
    private int getEarliestNextPlayableIndex(ArrayList<SegmentContainer> data, int after) {
        for(int i = after+1; i < data.size(); ++i)
            if(data.get(i).isOn())
                return i;
        if(mediaPlayer != null && mediaPlayer.isLooping())
            for(int i = 0; i <= after; ++i)
                if(data.get(i).isOn())
                    return i;
        return -1;
    }
    private int getEarliestPrevPlayableIndex(ArrayList<SegmentContainer> data, int before) {
        for(int i = before-1; i >= 0; --i)
            if(data.get(i).isOn())
                return i;
        if(mediaPlayer != null && mediaPlayer.isLooping())
            for(int i = data.size()-1; i >= before; --i)
                if(data.get(i).isOn())
                    return i;
        return -1;
    }
    //endregion

    //region Resizing RecyclerView Codes
    private void resizeRecyclerView() {
        binding.recyclerViewSegments.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                Rect recyclerRect = new Rect();
                Rect nestedScrollRect = new Rect();
                binding.recyclerViewSegments.getHitRect(recyclerRect);
                binding.nestedscrollmain.getHitRect(nestedScrollRect);

                if (recyclerRect.height() >= nestedScrollRect.height()) resizeRecyclerToMax();
                else resizeRecyclerToMatching();

                binding.recyclerViewSegments.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                binding.recyclerViewSegments.requestLayout();
            }
        });
    }
    private void resizeRecyclerToMax() { binding.recyclerViewSegments.setLayoutParams(recyclerMaxSizeParams); }
    private void resizeRecyclerToMatching() {
        binding.recyclerViewSegments.setLayoutParams(
                new LinearLayout.LayoutParams(
                        binding.recyclerViewSegments.getLayoutParams().width,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                )
        );
    }
    //endregion
}