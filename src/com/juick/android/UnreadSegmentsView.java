package com.juick.android;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;

/**
 * Created with IntelliJ IDEA.
 * User: san
 * Date: 9/3/12
 * Time: 10:49 PM
 * To change this template use File | Settings | File Templates.
 */
public class UnreadSegmentsView extends ListView {

    ArrayList<DatabaseService.Period> unreads;
    private PeriodListener listener;

    public interface PeriodListener {
        void onPeriodClicked(DatabaseService.Period period);
    }

    public void setListener(PeriodListener listener) {
        this.listener = listener;
    }

    public static void loadPeriods(final Activity activity, final Utils.Function<Void, ArrayList<DatabaseService.Period>> callback) {
        final Utils.ServiceGetter<DatabaseService> databaseServiceGetter = new Utils.ServiceGetter<DatabaseService>(activity, DatabaseService.class);
        databaseServiceGetter.getService(new Utils.ServiceGetter.Receiver<DatabaseService>() {
            @Override
            public void withService(final DatabaseService service) {
                super.withService(service);    //To change body of overridden methods use File | Settings | File Templates.
                new Thread("Calculating periods") {
                    @Override
                    public void run() {
                        final ArrayList<DatabaseService.Period> periods = service.getJuickPeriods(10);
                        activity.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                callback.apply(periods);
                            }
                        });
                    }
                }.start();
            }
        });

    }

    public UnreadSegmentsView(final Activity activity, ArrayList<DatabaseService.Period> periods) {
        super(activity);
        setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                DatabaseService.Period period = unreads.get(i);
                if (listener != null)
                    listener.onPeriodClicked(period);
            }
        });
        unreads = new ArrayList<DatabaseService.Period>();
        for (DatabaseService.Period period : periods) {
            if (!period.read)
                unreads.add(period);
        }
        final BaseAdapter listAdapter = new BaseAdapter() {
            @Override
            public int getCount() {
                return unreads.size();
            }

            @Override
            public Object getItem(int i) {
                return unreads.get(i);
            }

            @Override
            public long getItemId(int i) {
                return i;
            }

            @Override
            public View getView(int i, View view, ViewGroup viewGroup) {
                if (view == null)
                    view = activity.getLayoutInflater().inflate(android.R.layout.simple_list_item_1, null);
                if (view instanceof TextView) {
                    TextView tv = (TextView) view;
                    DatabaseService.Period period = unreads.get(i);
                    StringBuilder sb = new StringBuilder();
                    SimpleDateFormat sdf = new SimpleDateFormat("dd MMM HH:mm");
                    sb.append(sdf.format(period.startDate));
                    if (period.endDate != null) {
                        double hours = ((period.startDate.getTime() - period.endDate.getTime()) / 1000) / (60 * 60.0);
                        DecimalFormat df = new DecimalFormat();
                        df.setMaximumFractionDigits(1);
                        sb.append(" - " + df.format(hours) + " hours");
                    } else {
                        sb.append(" - many hours");
                    }
                    tv.setText(sb.toString());
                }
                MainActivity.restyleChildrenOrWidget(view);
                return view;
            }
        };
        UnreadSegmentsView.this.setAdapter(listAdapter);
        MainActivity.restyleChildrenOrWidget(UnreadSegmentsView.this);
    }
}
