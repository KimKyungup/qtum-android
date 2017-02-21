package org.qtum.mromanovsky.qtum.ui.fragment.WalletFragment;


import android.support.design.widget.AppBarLayout;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.content.ContextCompat;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.qtum.mromanovsky.qtum.R;
import org.qtum.mromanovsky.qtum.dataprovider.RestAPI.gsonmodels.History;
import org.qtum.mromanovsky.qtum.datastorage.QtumSharedPreference;
import org.qtum.mromanovsky.qtum.ui.activity.MainActivity.MainActivity;
import org.qtum.mromanovsky.qtum.ui.fragment.BaseFragment.BaseFragment;

import java.text.DecimalFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Locale;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

public class WalletFragment extends BaseFragment implements WalletFragmentView {

    public final int LAYOUT = R.layout.fragment_wallet;

    WalletFragmentPresenterImpl mWalletFragmentPresenter;
    TransactionAdapter mTransactionAdapter;

    @BindView(R.id.tv_public_key)
    TextView mTvPublicKey;
    @BindView(R.id.tv_balance)
    TextView mTvBalance;
    @BindView(R.id.fab)
    FloatingActionButton mFloatingActionButton;
    @BindView(R.id.ll_receive)
    LinearLayout mLinearLayoutReceive;
    @BindView(R.id.recycler_view)
    RecyclerView mRecyclerView;
    @BindView(R.id.swipe_refresh)
    SwipeRefreshLayout mSwipeRefreshLayout;
    @BindView(R.id.app_bar)
    AppBarLayout mAppBarLayout;
    @BindView(R.id.bt_qr_code)
    ImageButton mButtonQrCode;
    @BindView(R.id.progress_bar_balance)
    ProgressBar mProgressBarDialog;

    @OnClick({R.id.fab, R.id.ll_receive, R.id.bt_qr_code})
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.fab:
                mWalletFragmentPresenter.sharePubKey();
                break;
            case R.id.ll_receive:
                getPresenter().onClickReceive();
                break;
            case R.id.bt_qr_code:
                getPresenter().onClickQrCode();
        }
    }

    public static WalletFragment newInstance() {
        WalletFragment walletFragment = new WalletFragment();
        return walletFragment;
    }

    @Override
    protected void createPresenter() {
        mWalletFragmentPresenter = new WalletFragmentPresenterImpl(this);
    }

    @Override
    protected WalletFragmentPresenterImpl getPresenter() {
        return mWalletFragmentPresenter;
    }

    @Override
    protected int getLayout() {
        return LAYOUT;
    }

    @Override
    public void updateRecyclerView(List<History> historyList) {

        mTransactionAdapter = new TransactionAdapter(historyList);
        mRecyclerView.setAdapter(mTransactionAdapter);

        mSwipeRefreshLayout.setRefreshing(false);
    }

    @Override
    public void setAdapterNull() {
        mTransactionAdapter = null;
    }

    @Override
    public void updateBalance(double balance) {
        mTvBalance.setText(String.valueOf(balance));

        mTvBalance.setVisibility(View.VISIBLE);
        mProgressBarDialog.setVisibility(View.GONE);
    }

    @Override
    public void updatePubKey(String pubKey) {
        mTvPublicKey.setText(pubKey);
    }

    @Override
    public void startRefreshAnimation() {
        mSwipeRefreshLayout.setRefreshing(true);

        mTvBalance.setVisibility(View.GONE);
        mProgressBarDialog.setVisibility(View.VISIBLE);
    }

    @Override
    public void stopRefreshRecyclerAnimation() {
        mSwipeRefreshLayout.setRefreshing(false);
    }

    @Override
    public void initializeViews() {

        ((MainActivity) getActivity()).showBottomNavigationView();

        mRecyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));

        mSwipeRefreshLayout.setColorSchemeColors(ContextCompat.getColor(getContext(),R.color.colorAccent));
        mSwipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                getPresenter().onRefresh();
            }
        });

        mAppBarLayout.addOnOffsetChangedListener(new AppBarLayout.OnOffsetChangedListener() {
            @Override
            public void onOffsetChanged(AppBarLayout appBarLayout, int verticalOffset) {
                if (!mSwipeRefreshLayout.isRefreshing()) {
                    if (verticalOffset == 0) {
                        mSwipeRefreshLayout.setEnabled(true);
                    } else {
                        mSwipeRefreshLayout.setEnabled(false);
                    }
                }
            }
        });

    }

    public class TransactionAdapter extends RecyclerView.Adapter<TransactionHolder> {

        private List<History> mHistoryList;
        History mHistory;

        TransactionAdapter(List<History> historyList) {
            mHistoryList = historyList;
        }

        @Override
        public TransactionHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            LayoutInflater layoutInflater = LayoutInflater.from(parent.getContext());
            View view = layoutInflater.inflate(R.layout.item_transaction, parent, false);
            return new TransactionHolder(view);
        }

        @Override
        public void onBindViewHolder(TransactionHolder holder, int position) {
            mHistory = mHistoryList.get(position);
            holder.bindTransactionData(mHistory);
        }

        @Override
        public int getItemCount() {
            return mHistoryList.size();
        }
    }


    public class TransactionHolder extends RecyclerView.ViewHolder {

        @BindView(R.id.tv_value)
        TextView mTextViewValue;
        @BindView(R.id.tv_date)
        TextView mTextViewDate;
        @BindView(R.id.tv_id)
        TextView mTextViewID;
        @BindView(R.id.tv_operation_type)
        TextView mTextViewOperationType;
        @BindView(R.id.iv_icon)
        ImageView mImageViewIcon;

        Date date = new Date();
        long currentTime = date.getTime() / 1000L;

        TransactionHolder(View itemView) {
            super(itemView);
            itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    getPresenter().openTransactionFragment(getAdapterPosition());
                }
            });
            ButterKnife.bind(this, itemView);
        }

        void bindTransactionData(History history) {

            long transactionTime = history.getBlockTime();
            long delay = currentTime - transactionTime;
            String dateString;
            if (delay < 3600) {
                dateString = delay / 60 + " min ago";
            } else {

                Calendar calendarNow = Calendar.getInstance();
                calendarNow.set( Calendar.HOUR_OF_DAY, 0);
                calendarNow.set(Calendar.MINUTE, 0);
                calendarNow.set(Calendar.SECOND, 0);
                date = calendarNow.getTime();

                Date dateTransaction = new Date(transactionTime * 1000L);
                Calendar calendar = new GregorianCalendar();
                calendar.setTime(dateTransaction);
                if ((transactionTime - date.getTime() / 1000L) > 0) {
                    dateString = calendar.get(Calendar.HOUR) + ":" + calendar.get(Calendar.MINUTE);
                } else {
                    dateString = calendar.getDisplayName(Calendar.MONTH, Calendar.SHORT, Locale.US) + ", " + calendar.get(Calendar.DATE);
                }
            }
            mTextViewDate.setText(dateString);

            if (history.getAmount() > 0) {
                mTextViewOperationType.setText(R.string.received);
                mTextViewID.setText(history.getFromAddress());
                mImageViewIcon.setImageResource(R.drawable.ic_received);
                mTextViewOperationType.setTextColor(ContextCompat.getColor(getContext(),R.color.colorAccent));
            } else {
                mTextViewOperationType.setText(R.string.sent);
                mTextViewID.setText(history.getToAddress());
                mImageViewIcon.setImageResource(R.drawable.ic_sent);
                mTextViewOperationType.setTextColor(ContextCompat.getColor(getContext(), R.color.pink));
            }
            DecimalFormat df = new DecimalFormat("0");
            df.setMaximumFractionDigits(8);
            mTextViewValue.setText(df.format(history.getAmount() * (QtumSharedPreference.getInstance().getExchangeRates(getContext()))) + " QTUM");
        }
    }
}