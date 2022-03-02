package ark.noah.audiosegmentv2.ui.home;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.graphics.BlendModeColorFilterCompat;
import androidx.core.graphics.BlendModeCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;

import ark.noah.audiosegmentv2.R;

public class SegmentAdapter extends RecyclerView.Adapter<SegmentAdapter.ViewHolder> {
    private ArrayList<SegmentContainer> mData;
    private SegmentAdapterToPlayerTransactionInterface myInterface;

    Color colorEnabled, colorDisabled;

    // 아이템 뷰를 저장하는 뷰홀더 클래스.
    public class ViewHolder extends RecyclerView.ViewHolder {
        ConstraintLayout Layout;
        TextView Timestamp, Description;
        ImageView State;
        SegmentContainer segmentContainer;
        ImageButton Loop;

        ViewHolder(View itemView) {
            super(itemView);

            Layout = itemView.findViewById(R.id.rec_item_seg_bg);
            Timestamp = itemView.findViewById(R.id.rec_seg_tv_timestamp);
            Description = itemView.findViewById(R.id.rec_seg_tv_description);
            State = itemView.findViewById(R.id.rec_seg_img_state);
            Loop = itemView.findViewById(R.id.rec_seg_btn_repeat);

            Loop.setOnClickListener(view -> {
                int condition = (segmentContainer.getCondition() & SegmentContainer.CONDITION_LOOPYN) == SegmentContainer.CONDITION_LOOPYN ? segmentContainer.getCondition() - SegmentContainer.CONDITION_LOOPYN : segmentContainer.getCondition() + SegmentContainer.CONDITION_LOOPYN;
                segmentContainer.setCondition(condition);
                updateItem(this.getAdapterPosition(), segmentContainer);
                notifyItemChanged(this.getAdapterPosition());
            });

            Layout.setOnClickListener(view -> {
                int condition = (segmentContainer.getCondition() & SegmentContainer.CONDITION_ONOFF) == SegmentContainer.CONDITION_ONOFF ? segmentContainer.getCondition() - SegmentContainer.CONDITION_ONOFF : segmentContainer.getCondition() + SegmentContainer.CONDITION_ONOFF;
                segmentContainer.setCondition(condition);
                updateItem(this.getAdapterPosition(), segmentContainer);
                notifyItemChanged(this.getAdapterPosition());
            });
            Layout.setOnLongClickListener(view -> {
                myInterface.openDialog(this.getAdapterPosition(), segmentContainer);
                return true;    //prevents shortclick to ignite
            });
        }
    }

    // 생성자에서 데이터 리스트 객체를 전달받음.
    public SegmentAdapter(ArrayList<SegmentContainer> list) {
        mData = list;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        Context context = parent.getContext();
        LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        View view = inflater.inflate(R.layout.recycler_item_segments, parent, false);

        if(colorEnabled == null) {
            TypedValue typedValue = new TypedValue();
            context.getTheme().resolveAttribute(R.attr.TextColor, typedValue, true);
            colorEnabled = Color.valueOf(typedValue.data);
            context.getTheme().resolveAttribute(R.attr.TextColorDisabled, typedValue, true);
            colorDisabled = Color.valueOf(typedValue.data);
        }

        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        SegmentContainer mContainer = mData.get(position);

        holder.Description.setText(mContainer.getDescription());
        holder.segmentContainer = mContainer;

        String overallTimestamp = mContainer.getStart_timestampAsString() + " ~ " + mContainer.getEnd_timestampAsString();
        holder.Timestamp.setText(overallTimestamp);

        if(mContainer.isOn())
            holder.State.setBackgroundColor(colorEnabled.toArgb());
        else holder.State.setBackgroundColor(colorDisabled.toArgb());

        if(mContainer.isLooping()) {
            Drawable drawable = holder.Loop.getDrawable();
            drawable.setColorFilter(BlendModeColorFilterCompat.createBlendModeColorFilterCompat(colorEnabled.toArgb(), BlendModeCompat.SRC_ATOP));
            holder.Loop.setImageDrawable(drawable);
        }
        else {
            Drawable drawable = holder.Loop.getDrawable();
            drawable.setColorFilter(BlendModeColorFilterCompat.createBlendModeColorFilterCompat(colorDisabled.toArgb(), BlendModeCompat.SRC_ATOP));
            holder.Loop.setImageDrawable(drawable);
        }
    }

    @Override
    public int getItemCount() {
        return mData.size();
    }

    public SegmentContainer getItemAtPosition(int position) {
        return mData.get(position);
    }
    public void setItemAtPosition(int position, SegmentContainer segmentContainer) {
        mData.set(position, segmentContainer);
    }

    public ArrayList<SegmentContainer> getmData() {
        return mData;
    }

    public SegmentContainer findMDataAtPosition(int position) {
        return mData.get(position);
    }
    public int findPosition(SegmentContainer givenContainer) {
        int temp = 0;
        for (SegmentContainer segmentContainer : mData) {
            if(segmentContainer == givenContainer) {
                return temp;
            }
            ++temp;
        }
        return -1;
    }

    public void updateData(ArrayList<SegmentContainer> itemList) {
        this.mData = itemList;
        notifyDataSetChanged();
    }
    public void updateItem(int position, SegmentContainer item) {
        mData.set(position, item);
        notifyItemChanged(position);
    }

    public void addNewDataset(ArrayList<SegmentContainer> itemList) {
        this.mData.addAll(0, itemList);
        notifyDataSetChanged();
    }
    public void addNewData(SegmentContainer item) {
        this.mData.add(item);
        notifyItemInserted(mData.size()-1);
    }
    public void addNewDataAtPosition(int position, SegmentContainer item) {
        if(position < this.mData.size())
            this.mData.add(position, item);
        else
            this.mData.add(item);
        notifyItemInserted(position);
    }

    public void removeItem(SegmentContainer item) {
        int position = mData.indexOf(item);
        mData.remove(item);
        notifyItemRemoved(position);
    }
    public void removeItem(int position) {
        mData.remove(position);
        notifyItemRemoved(position);
    }

    public boolean isDatasetEmpty() {
        return mData == null || mData.isEmpty();
    }

    public void setMyInterface(SegmentAdapterToPlayerTransactionInterface myInterface) { this.myInterface = myInterface; }

    interface SegmentAdapterToPlayerTransactionInterface {
        void openDialog(int position, SegmentContainer segmentContainer);
    }
}
