package ro.meteoalarm.android;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

import ro.meteoalarm.android.core.Theme;
import ro.meteoalarm.android.core.Tr;

/**
 * v2.120 (Viorel 2026-11-19): the Export and Import windows. A switch for each kind of data; in
 * Export the towns and the parcels also open (▾) a list to tick them one by one. The choice is kept.
 */
final class BackupPanel {
	interface Done {
		void run(Backup.Sel sel, int button);
	}

	private final Context ctx;
	private final boolean dark;
	private final Typeface reg, bold;
	private final float dp;
	private final ro.meteoalarm.android.core.Screen screen;
	private Dialog d;
	private LinearLayout rows, body, col;
	/** v2.140: ‹ at the left of the title instead of ✕ (the log's lines, back to the Log). */
	private boolean backStyle;
	/** v2.130 (Viorel): the text size of the window (the Aa button, as on Help). */
	private float ts;

	BackupPanel(Context ctx, boolean dark, Typeface reg, Typeface bold, ro.meteoalarm.android.core.Screen screen) {
		this.ctx = ctx;
		this.dark = dark;
		this.reg = reg;
		this.bold = bold;
		this.screen = screen;
		this.dp = ctx.getResources().getDisplayMetrics().density;
		this.ts = TextSize.get(ctx);
	}

	private int px(float v) {
		return Math.round(v * dp);
	}

	private int ink() {
		return Theme.ink(dark);
	}

	private int dim() {
		return Theme.dim(dark);
	}

	private int accent() {
		return Theme.accent(dark);
	}

	static String[] titles() {
		return new String[] { Tr.t("My towns and favourites", "Localitățile mele și favoritele"), Tr.t("Parcels", "Parcele"),
				Tr.t("Parcel notifications", "Notificările parcelelor"), Tr.t("Parcel bin", "Coșul de parcele"),
				Tr.t("Places visited (villages)", "Locuri vizitate (sate)"), Tr.t("Look and language", "Aspect și limbă"),
				Tr.t("Notifications and alerts", "Notificări și alerte"), Tr.t("Map", "Hartă"), Tr.t("Widget settings", "Setările widget-urilor"),
				Tr.t("Earthquake history", "Istoricul cutremurelor"), Tr.t("App log", "Jurnalul aplicației"),
				Tr.t("Crops and growing data", "Culturi și date de cultivare") };
	}

	static String[] subs() {
		return new String[] { "", Tr.t("name, corners, points", "nume, colțuri, puncte"),
				Tr.t("only for the chosen parcels", "doar pentru parcelele alese"), Tr.t("parcels deleted in the last 15 days", "parcelele șterse în ultimele 15 zile"),
				"", Tr.t("theme, colours, icons, language, text size, units", "temă, culori, iconuri, limbă, mărimea textului, unități"),
				Tr.t("rain, warnings, earthquakes, tornadoes", "ploaie, avertizări, cutremure, tornade"), Tr.t("satellite or map, weather layer", "satelit sau hartă, stratul meteo"),
				Tr.t("background, colours, days or hours", "fundal, culori, zile sau ore"), Tr.t("EMSC and institute values", "valorile EMSC și ale institutelor"),
				Tr.t("last 5 days, for troubleshooting", "ultimele 5 zile, pentru depanare"),
				Tr.t("crop, sowing date, area, thresholds", "cultura, data semănatului, suprafața, pragurile") };
	}

	/**
	 * The window: TITLE with Aa and ✕, a line under it, the BODY (for Export and Import the rows, which
	 * scroll), and the 2 buttons B1 (filled) and B2. v2.130 (Viorel): a sheet of matte glass like the
	 * ⚙︎ menu (readable on the light theme too), the camera's band above it, the Aa button for the
	 * text size. KEEP: the buttons do not close the window (the Log).
	 */
	private void frame(String title, String line, String b1, String b2, final Done done, final Backup.Sel sel, String foot, final boolean keep) {
		d = new Dialog(ctx, android.R.style.Theme_DeviceDefault_Dialog_NoActionBar);
		final Window w = d.getWindow();
		w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0));
		final FrameLayout root = new FrameLayout(ctx);
		col = new LinearLayout(ctx);
		col.setOrientation(LinearLayout.VERTICAL);
		col.setPadding(px(18), px(12), px(18), px(16));
		android.graphics.drawable.GradientDrawable pb = Glass.panelBg(dark, 26 * dp, dp);
		pb.setColor(Glass.alpha(Theme.page(dark), dark ? 0.9f : 0.94f));
		col.setBackground(pb);
		final android.util.DisplayMetrics m = ctx.getResources().getDisplayMetrics();
		// v2.140 (Viorel): no band of its own; the shade starts under the main screen's band,
		// which stays as it is (the same clock everywhere).
		final View shade = new View(ctx);
		shade.setBackgroundColor(0); // v2.142 (Viorel): no shade, the screen behind stays as it is
		final FrameLayout.LayoutParams shl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
		shl.topMargin = TopBand.shadeTop(ctx, null);
		root.addView(shade, shl);
		final FrameLayout.LayoutParams cl = new FrameLayout.LayoutParams(Math.min(m.widthPixels - px(24), px(560)), ViewGroup.LayoutParams.MATCH_PARENT,
				Gravity.CENTER_HORIZONTAL);
		cl.topMargin = Math.max(shl.topMargin, px(12)) + px(8);
		cl.bottomMargin = px(14);
		root.addView(col, cl);
		root.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
			public android.view.WindowInsets onApplyWindowInsets(View v, android.view.WindowInsets in) {
				int bh = TopBand.shadeTop(ctx, root);
				shl.topMargin = bh;
				cl.topMargin = Math.max(bh, px(12)) + px(8);
				shade.requestLayout();
				col.requestLayout();
				return in;
			}
		});
		LinearLayout head = new LinearLayout(ctx);
		head.setGravity(Gravity.CENTER_VERTICAL);
		if (backStyle) {
			TextView bk = text(26, true, ink());
			bk.setText("‹");
			bk.setGravity(Gravity.CENTER);
			bk.setPadding(0, 0, 0, px(4));
			bk.setContentDescription(Tr.t("Back", "Înapoi"));
			bk.setBackground(Glass.pill(Glass.disc(dark), Glass.edge(dark), 22 * dp, dp));
			bk.setOnClickListener(new View.OnClickListener() {
				public void onClick(View v) {
					d.dismiss();
				}
			});
			LinearLayout.LayoutParams kl = new LinearLayout.LayoutParams(px(44), px(44));
			kl.rightMargin = px(12);
			head.addView(bk, kl);
		}
		FitText t = new FitText(ctx, 1, 0.5f);
		t.setTypeface(bold);
		t.setTextColor(ink());
		t.setIncludeFontPadding(false);
		t.setTextSize(26);
		t.setText(title);
		head.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
		// Aa: the text-size bar, as on Help.
		final SizeOverlay sizer = screen != null ? new SizeOverlay(ctx, screen) : null;
		if (sizer != null) {
			TextView aa = text(17, true, ink());
			aa.setText("Aa");
			aa.setTag(R.id.widget_root, null);
			aa.setTextSize(17);
			aa.setGravity(Gravity.CENTER);
			aa.setContentDescription(Tr.t("Text size", "Mărimea textului"));
			aa.setBackground(Glass.pill(Glass.disc(dark), Glass.edge(dark), 22 * dp, dp));
			aa.setOnClickListener(new View.OnClickListener() {
				public void onClick(View v) {
					sizer.show(new SizeOverlay.Listener() {
						public void sized(float s, boolean save) {
							ts = s;
							TextSize.apply(col, ts);
						}
					});
				}
			});
			LinearLayout.LayoutParams al = new LinearLayout.LayoutParams(px(56), px(44));
			al.rightMargin = px(10);
			al.leftMargin = px(8);
			head.addView(aa, al);
		}
		View x = new View(ctx);
		x.setBackground(Glass.closeIcon(dark, dp));
		x.setContentDescription(Tr.t("Close", "Închide"));
		x.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				d.dismiss();
			}
		});
		if (!backStyle) head.addView(x, new LinearLayout.LayoutParams(px(44), px(44)));
		col.addView(head);
		if (line != null) {
			TextView l = text(13, false, dim());
			l.setText(line);
			l.setPadding(0, px(6), 0, px(10));
			col.addView(l);
		} else col.addView(new View(ctx), new LinearLayout.LayoutParams(1, px(10)));
		body = new LinearLayout(ctx);
		body.setOrientation(LinearLayout.VERTICAL);
		col.addView(body, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
		if (sel != null) {
			ScrollView sv = new ScrollView(ctx);
			rows = new LinearLayout(ctx);
			rows.setOrientation(LinearLayout.VERTICAL);
			rows.setBackground(Glass.cardBg(dark, 18 * dp, dp));
			sv.addView(rows);
			body.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		}
		LinearLayout bs = new LinearLayout(ctx);
		bs.setPadding(0, px(14), 0, 0);
		for (int i = 0; b1 != null && i < 2; i++) {
			final int k = i + 1;
			FitText b = new FitText(ctx, 1, 0.5f);
			b.setTypeface(bold);
			b.setTextColor(i == 0 ? Theme.onPastel(dark) : ink());
			b.setIncludeFontPadding(false);
			b.setTextSize(17);
			b.setText(i == 0 ? b1 : b2);
			b.setGravity(Gravity.CENTER);
			b.setPadding(px(8), 0, px(8), 0);
			b.setBackground(Glass.pill(i == 0 ? Theme.pastel(dark) : Glass.disc(dark), i == 0 ? 0 : Glass.edge(dark), 24 * dp, dp));
			b.setOnClickListener(new View.OnClickListener() {
				public void onClick(View v) {
					if (!keep) d.dismiss();
					done.run(sel, k);
				}
			});
			LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(0, px(48), 1);
			if (i > 0) bl.leftMargin = px(12);
			bs.addView(b, bl);
		}
		if (b1 != null) col.addView(bs);
		if (foot != null) {
			TextView f = text(12, false, dim());
			f.setText(foot);
			f.setGravity(Gravity.CENTER);
			f.setPadding(0, px(8), 0, 0);
			col.addView(f);
		}
		if (sizer != null) root.addView(sizer, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
		d.setContentView(root);
		w.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
		TopBand.fullScreen(w, dark);
		if (android.os.Build.VERSION.SDK_INT >= 30) w.setDecorFitsSystemWindows(false);
		w.getDecorView().setPadding(0, 0, 0, 0);
		w.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
	}

	private TextView text(float sp, boolean b, int color) {
		TextView t = new TextView(ctx);
		t.setTypeface(b ? bold : reg);
		TextSize.size(t, sp, ts);
		t.setTextColor(color);
		t.setIncludeFontPadding(false);
		return t;
	}

	private void sep() {
		if (rows.getChildCount() == 0) return;
		View v = new View(ctx);
		v.setBackgroundColor(Glass.edge(dark));
		rows.addView(v, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, Math.round(dp))));
	}

	/** One row: title, sub line, switch (and ▾ when OPENS is given). Returns the sub line's view. */
	private TextView row(String title, String sub, final boolean[] on, final int k, boolean enabled, final View list) {
		sep();
		LinearLayout r = new LinearLayout(ctx);
		r.setGravity(Gravity.CENTER_VERTICAL);
		r.setPadding(px(14), px(12), px(12), px(12));
		LinearLayout tx = new LinearLayout(ctx);
		tx.setOrientation(LinearLayout.VERTICAL);
		// v2.130 (Viorel): the title on one row, as large as the room up to the switch allows.
		FitText t = new FitText(ctx, 1, 0.45f);
		t.setTypeface(reg);
		t.setTextColor(ink());
		t.setIncludeFontPadding(false);
		TextSize.size(t, 16, ts);
		t.setText(title);
		tx.addView(t, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		TextView s = text(12.5f, false, dim());
		s.setText(sub);
		s.setPadding(0, px(3), 0, 0);
		if (sub.length() > 0) tx.addView(s);
		r.addView(tx, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
		final Switch sw = new Switch(on[k] && enabled);
		r.addView(sw, new LinearLayout.LayoutParams(px(46), px(26)));
		if (!enabled) {
			r.setAlpha(0.4f);
			on[k] = false;
		} else {
			View.OnClickListener tap = new View.OnClickListener() {
				public void onClick(View v) {
					on[k] = !on[k];
					sw.on = on[k];
					sw.invalidate();
				}
			};
			r.setOnClickListener(tap);
		}
		if (list != null) {
			final TextView ch = text(15, true, ink());
			ch.setText("▾");
			ch.setGravity(Gravity.CENTER);
			ch.setBackground(Glass.pill(Glass.disc(dark), 0, 15 * dp, dp));
			ch.setOnClickListener(new View.OnClickListener() {
				public void onClick(View v) {
					boolean open = list.getVisibility() != View.VISIBLE;
					list.setVisibility(open ? View.VISIBLE : View.GONE);
					ch.setText(open ? "▴" : "▾");
				}
			});
			LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(px(30), px(30));
			cl.leftMargin = px(10);
			r.addView(ch, cl);
		}
		rows.addView(r);
		if (list != null) {
			list.setVisibility(View.GONE);
			rows.addView(list);
		}
		return s;
	}

	/** A list of tick boxes in a box of at most ~5 rows that scrolls inside; COUNT shows "n din m". */
	private View ticks(final String[] names, final String[] details, final boolean[] ticked, final TextView count, final String tail, final Runnable changed) {
		LinearLayout box = new LinearLayout(ctx);
		box.setOrientation(LinearLayout.VERTICAL);
		box.setBackground(Glass.pill(Glass.alpha(0xFF000000, dark ? 0.18f : 0.05f), Glass.edge(dark), 14 * dp, dp));
		LinearLayout bar = new LinearLayout(ctx);
		bar.setGravity(Gravity.CENTER_VERTICAL);
		bar.setPadding(px(10), px(8), px(10), px(8));
		final TextView n = text(13, false, dim());
		final Box[] boxes = new Box[names.length];
		final Runnable recount = new Runnable() {
			public void run() {
				int c = 0;
				for (boolean b : ticked) if (b) c++;
				String s = c + " " + Tr.t("of", "din") + " " + ticked.length;
				n.setText(s);
				count.setText(s + " " + Tr.t("chosen", "alese") + tail);
				changed.run();
			}
		};
		for (int i = 0; i < 2; i++) {
			final boolean all = i == 0;
			TextView p = text(13, true, ink());
			p.setText(all ? Tr.t("All", "Toate") : Tr.t("None", "Niciuna"));
			p.setPadding(px(12), px(5), px(12), px(5));
			p.setBackground(Glass.pill(Glass.disc(dark), 0, 13 * dp, dp));
			p.setOnClickListener(new View.OnClickListener() {
				public void onClick(View v) {
					for (int k = 0; k < ticked.length; k++) {
						ticked[k] = all;
						boxes[k].on = all;
						boxes[k].invalidate();
					}
					recount.run();
				}
			});
			LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
			if (i > 0) pl.leftMargin = px(8);
			bar.addView(p, pl);
		}
		bar.addView(new View(ctx), new LinearLayout.LayoutParams(0, 1, 1));
		bar.addView(n);
		box.addView(bar);
		LinearLayout items = new LinearLayout(ctx);
		items.setOrientation(LinearLayout.VERTICAL);
		for (int i = 0; i < names.length; i++) {
			final int k = i;
			LinearLayout r = new LinearLayout(ctx);
			r.setGravity(Gravity.CENTER_VERTICAL);
			r.setPadding(px(10), px(7), px(10), px(7));
			final Box b = new Box(ticked[i]);
			boxes[i] = b;
			r.addView(b, new LinearLayout.LayoutParams(px(22), px(22)));
			LinearLayout tx = new LinearLayout(ctx);
			tx.setOrientation(LinearLayout.VERTICAL);
			tx.setPadding(px(10), 0, 0, 0);
			TextView a = text(15, false, ink());
			a.setText(names[i]);
			a.setSingleLine(true);
			a.setEllipsize(android.text.TextUtils.TruncateAt.END);
			tx.addView(a);
			if (details[i].length() > 0) {
				TextView dd = text(11.5f, false, dim());
				dd.setText(details[i]);
				dd.setSingleLine(true);
				dd.setEllipsize(android.text.TextUtils.TruncateAt.END);
				dd.setPadding(0, px(2), 0, 0);
				tx.addView(dd);
			}
			r.addView(tx, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
			r.setOnClickListener(new View.OnClickListener() {
				public void onClick(View v) {
					ticked[k] = !ticked[k];
					b.on = ticked[k];
					b.invalidate();
					recount.run();
				}
			});
			items.addView(r);
		}
		// The list scrolls inside its box (the window scrolls around it).
		ScrollView sv = new ScrollView(ctx) {
			@Override
			public boolean onInterceptTouchEvent(android.view.MotionEvent e) {
				getParent().requestDisallowInterceptTouchEvent(true);
				return super.onInterceptTouchEvent(e);
			}
		};
		sv.setVerticalScrollBarEnabled(true);
		sv.setScrollbarFadingEnabled(false);
		sv.addView(items);
		box.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, names.length > 5 ? px(5 * 46) : ViewGroup.LayoutParams.WRAP_CONTENT));
		LinearLayout wrap = new LinearLayout(ctx);
		wrap.setPadding(px(10), 0, px(10), px(10));
		wrap.addView(box, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		recount.run();
		return wrap;
	}

	/** "Călărași" and "Călărași · România" from a town line (county|name|… or cc|state|name|…). */
	static String[] townOf(String line, String kind) {
		String[] p = line.split("\\|");
		if (p.length >= 3 && p[0].matches("[a-z]{2}")) return new String[] { p[2], kind + " · " + p[1] + ", " + p[0].toUpperCase(java.util.Locale.ROOT) };
		return new String[] { p.length > 1 ? p[1] : line, kind + (p.length > 0 && p[0].length() > 0 ? " · " + p[0] : "") };
	}

	/** The Export window; DONE gets button 1 = Save, 2 = Share. */
	void export(final Done done) {
		final Backup.Sel sel = Backup.Sel.load(ctx);
		frame(Tr.t("Export", "Export"), Tr.t("Choose what goes into the file · ▾ opens the list", "Alege ce intră în fișier · ▾ deschide lista"),
				Tr.t("Save", "Salvează"), Tr.t("Share", "Trimite"), new Done() {
					public void run(Backup.Sel s, int b) {
						s.save(ctx);
						done.run(s, b);
					}
				}, sel, Tr.t("The choice is kept", "Alegerea rămâne ținută minte"), false);
		final int[] n = Backup.what(ctx, null);
		String[] ti = titles(), su = subs();
		android.content.SharedPreferences p = Fetcher.prefs(ctx);
		// Towns
		final java.util.List<String> tl = new java.util.ArrayList<String>();
		java.util.List<String[]> td = new java.util.ArrayList<String[]>();
		for (String l : Backup.lines(p, "myCities")) { tl.add(l); td.add(townOf(l, Tr.t("my town", "localitatea mea"))); }
		for (String l : Backup.lines(p, "favs")) { tl.add(l); td.add(townOf(l, Tr.t("favourite", "favorită"))); }
		for (String l : Backup.lines(p, "favsUS")) { tl.add(l); td.add(townOf(l, Tr.t("favourite", "favorită"))); }
		final boolean[] tt = new boolean[tl.size()];
		String[] tn = new String[tl.size()], tdd = new String[tl.size()];
		for (int i = 0; i < tl.size(); i++) {
			tt[i] = !sel.offTowns.contains(tl.get(i));
			tn[i] = td.get(i)[0];
			tdd[i] = td.get(i)[1];
		}
		TextView[] cnt = new TextView[1];
		cnt[0] = text(12, false, dim());
		View tlist = tl.isEmpty() ? null : ticks(tn, tdd, tt, cnt[0], "", new Runnable() {
			public void run() {
				sel.offTowns.clear();
				for (int i = 0; i < tt.length; i++) if (!tt[i]) sel.offTowns.add(tl.get(i));
			}
		});
		TextView s0 = row(ti[0], tl.isEmpty() ? Tr.t("nothing to save", "nimic de salvat") : cnt[0].getText().toString(), sel.on, Backup.TOWNS, !tl.isEmpty(), tlist);
		link(s0, cnt[0]);
		// Parcels
		final List<Parcels.Parcel> pl = Parcels.all(ctx);
		final boolean[] pt = new boolean[pl.size()];
		String[] pn = new String[pl.size()], pd = new String[pl.size()];
		for (int i = 0; i < pl.size(); i++) {
			Parcels.Parcel x = pl.get(i);
			pt[i] = !sel.offParcels.contains(x.id);
			pn[i] = x.name;
			pd[i] = parcelLine(x);
		}
		final TextView pc = text(12, false, dim());
		View plist = pl.isEmpty() ? null : ticks(pn, pd, pt, pc, " · " + su[Backup.PARCELS], new Runnable() {
			public void run() {
				sel.offParcels.clear();
				for (int i = 0; i < pt.length; i++) if (!pt[i]) sel.offParcels.add(pl.get(i).id);
			}
		});
		TextView s1 = row(ti[1], pl.isEmpty() ? Tr.t("nothing to save", "nimic de salvat") : pc.getText().toString(), sel.on, Backup.PARCELS, !pl.isEmpty(), plist);
		link(s1, pc);
		for (int k : Backup.ORDER) {
			if (k == Backup.TOWNS || k == Backup.PARCELS) continue;
			boolean has = n[k] > 0 || k == Backup.LOOK || k == Backup.LOG;
			String sub = has ? su[k] : Tr.t("nothing to save", "nimic de salvat");
			if (k == Backup.WIDGETS && has) sub = n[k] + " " + Tr.t("widgets", "widget-uri") + ": " + su[k];
			if (k == Backup.CROPS && has) sub = n[k] + " " + Tr.t("parcels", "parcele") + ": " + su[k];
			row(ti[k], sub, sel.on, k, has, null);
		}
		show();
	}

	/** The count line under a row follows the list's count. */
	private void link(final TextView sub, final TextView count) {
		link(sub, count, "");
	}

	private void link(final TextView sub, final TextView count, final String pre) {
		count.addTextChangedListener(new android.text.TextWatcher() {
			public void beforeTextChanged(CharSequence s, int a, int b, int c) {
			}

			public void onTextChanged(CharSequence s, int a, int b, int c) {
				sub.setText(pre + s);
			}

			public void afterTextChanged(android.text.Editable s) {
			}
		});
	}

	/**
	 * The Import window for file O; DONE gets the switches and button 1 = Add, 2 = Replace. v2.130
	 * (Viorel): like Export, the towns and the parcels of the file in lists to tick one by one.
	 */
	void imports(org.json.JSONObject o, String line, Done done) {
		final Backup.Sel sel = new Backup.Sel();
		frame(Tr.t("Import", "Import"), line, Tr.t("Add", "Adaugă"), Tr.t("Replace", "Înlocuiește"), done, sel,
				Tr.t("Add keeps what you have; Replace makes it as in the file, only for the rows switched on.",
						"Adaugă păstrează ce ai pe telefon; Înlocuiește face totul ca în fișier, doar pentru rândurile pornite."), false);
		int[] n = Backup.what(ctx, o);
		String[] ti = titles(), su = subs();
		String in = Tr.t("in the file: ", "în fișier: ");
		for (int k : Backup.ORDER) {
			if (k == Backup.LOG) continue;
			boolean has = n[k] > 0;
			sel.on[k] = has;
			String sub;
			View list = null;
			if (!has) sub = Tr.t("not in the file", "nu e în fișier");
			else if (k == Backup.TOWNS) {
				final java.util.List<String> tl = new java.util.ArrayList<String>();
				java.util.List<String[]> td = new java.util.ArrayList<String[]>();
				String[] keys = { "myCities", "favs", "favsUS" };
				for (int i = 0; i < 3; i++)
					for (String l : o.optString(keys[i], "").split("\n")) if (l.trim().length() > 0) {
						tl.add(l);
						td.add(townOf(l, i == 0 ? Tr.t("my town", "localitatea mea") : Tr.t("favourite", "favorită")));
					}
				final boolean[] tt = new boolean[tl.size()];
				String[] tn = new String[tl.size()], tdd = new String[tl.size()];
				for (int i = 0; i < tl.size(); i++) {
					tt[i] = true;
					tn[i] = td.get(i)[0];
					tdd[i] = td.get(i)[1];
				}
				final TextView cnt = text(12, false, dim());
				list = tl.isEmpty() ? null : ticks(tn, tdd, tt, cnt, "", new Runnable() {
					public void run() {
						sel.offTowns.clear();
						for (int i = 0; i < tt.length; i++) if (!tt[i]) sel.offTowns.add(tl.get(i));
					}
				});
				sub = in + cnt.getText();
				TextView s0 = row(ti[k], sub, sel.on, k, true, list);
				link(s0, cnt, in);
				continue;
			} else if (k == Backup.PARCELS) {
				final List<Parcels.Parcel> pl = Backup.fileParcels(o);
				final boolean[] pt = new boolean[pl.size()];
				String[] pn = new String[pl.size()], pd = new String[pl.size()];
				for (int i = 0; i < pl.size(); i++) {
					pt[i] = true;
					pn[i] = pl.get(i).name;
					pd[i] = parcelLine(pl.get(i));
				}
				final TextView cnt = text(12, false, dim());
				list = pl.isEmpty() ? null : ticks(pn, pd, pt, cnt, "", new Runnable() {
					public void run() {
						sel.offParcels.clear();
						for (int i = 0; i < pt.length; i++) if (!pt[i]) sel.offParcels.add(pl.get(i).id);
					}
				});
				TextView s1 = row(ti[k], in + cnt.getText(), sel.on, k, true, list);
				link(s1, cnt, in);
				continue;
			} else if (k == Backup.WIDGETS) sub = in + n[k] + " " + Tr.t("widgets", "widget-uri");
			else if (k == Backup.CROPS) sub = in + n[k] + " " + Tr.t("parcels", "parcele") + ": " + su[k];
			else if (k == Backup.LOOK || k == Backup.ALERT || k == Backup.MAPS) sub = in + n[k] + " " + Tr.t("settings", "setări");
			else sub = in + n[k];
			row(ti[k], sub, sel.on, k, has, null);
		}
		show();
	}

	/** "orz · semănat 11.09 · 44.2307, 27.1578" for a parcel's line in a list. */
	static String parcelLine(Parcels.Parcel x) {
		ro.meteoalarm.android.core.Agro.Crop c = x.crop();
		StringBuilder b = new StringBuilder();
		if (c != null) b.append(c.name());
		if (x.sow.length() >= 10) b.append(b.length() > 0 ? " · " : "").append(Tr.t("sown ", "semănat ")).append(x.sow.substring(8, 10)).append('.').append(x.sow.substring(5, 7));
		b.append(b.length() > 0 ? " · " : "").append(String.format(java.util.Locale.ROOT, "%.4f, %.4f", x.lat, x.lon));
		return b.toString();
	}

	/**
	 * v2.130 (Viorel): the Log in the same matte window. v2.140 (Viorel): no lines here any more (Aa
	 * was slow with them): what the log is, its size, lines and errors, and at the bottom of the
	 * screen the buttons See the log (its lines in a window of their own), Share (1) and Save (2).
	 */
	void log(final String text, final Done done) {
		frame(Tr.t("Log", "Jurnal"), null, null, null, done, null, null, true);
		LinearLayout info = new LinearLayout(ctx);
		info.setOrientation(LinearLayout.VERTICAL);
		info.setBackground(Glass.cardBg(dark, 18 * dp, dp));
		info.setPadding(px(14), px(12), px(14), px(14));
		TextView h = text(15, true, ink());
		h.setText(Tr.t("What the log is", "Ce este jurnalul"));
		info.addView(h);
		TextView a = text(13, false, dim());
		a.setText(Tr.t("Everything the app does, with the time: the weather, warnings and earthquake downloads, the notifications, the taps and gestures, the sensors, the widgets and the errors, with their reason. It keeps the last 5 days; errors stay 10 days.",
				"Tot ce face aplicația, cu ora: descărcările de vreme, avertizări și cutremure, notificările, atingerile și gesturile, senzorii, widget-urile și erorile, cu motivul lor. Păstrează ultimele 5 zile; erorile rămân 10 zile."));
		a.setPadding(0, px(6), 0, 0);
		info.addView(a);
		TextView b = text(13, false, dim());
		b.setText(Tr.t("When something does not work, tap Share or Save and send it to the author.",
				"Când ceva nu merge, apasă Trimite sau Salvează și trimite-l autorului."));
		b.setPadding(0, px(6), 0, 0);
		info.addView(b);
		// Size, lines, errors (v2.141: only the real errors not yet fixed; the network's are warnings).
		int[] n = Log.tally(text);
		int lines = n[0], errs = n[1], warns = n[2];
		long bytes;
		try {
			bytes = text.getBytes("UTF-8").length;
		} catch (Exception e) {
			bytes = text.length();
		}
		String size = bytes >= 1024 * 1024 ? String.format(java.util.Locale.ROOT, "%.1f MB", bytes / 1048576f).replace('.', Tr.lang().equals("en") ? '.' : ',')
				: Math.max(1, bytes / 1024) + " KB";
		LinearLayout st = new LinearLayout(ctx);
		st.setPadding(0, px(12), 0, 0);
		String[][] cells = { { Tr.t("Size", "Mărime"), size }, { Tr.t("Lines", "Linii"), String.format(java.util.Locale.FRANCE, "%,d", lines).replace('\u202f', ' ').replace('\u00a0', ' ') },
				{ Tr.t("Errors", "Erori"), String.valueOf(errs) } };
		for (int i = 0; i < 3; i++) {
			// v2.141 (Viorel): the boxes keep their size; Aa does not change them, the text fits inside.
			LinearLayout c = new LinearLayout(ctx);
			c.setOrientation(LinearLayout.VERTICAL);
			c.setGravity(Gravity.CENTER);
			c.setBackground(Glass.pill(Glass.disc(dark), 0, 12 * dp, dp));
			c.setPadding(px(10), px(6), px(10), px(6));
			FitText k = new FitText(ctx, 1, 0.5f);
			k.setTypeface(reg);
			k.setTextColor(dim());
			k.setIncludeFontPadding(false);
			k.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 12);
			k.setGravity(Gravity.CENTER);
			k.setText(cells[i][0]);
			c.addView(k, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
			FitText v = new FitText(ctx, 1, 0.4f);
			v.setTypeface(bold);
			v.setTextColor(i == 2 && errs > 0 ? 0xFFE0603F : ink());
			v.setIncludeFontPadding(false);
			v.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 18);
			v.setGravity(Gravity.CENTER);
			v.setText(cells[i][1]);
			LinearLayout.LayoutParams vl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
			vl.topMargin = px(4);
			c.addView(v, vl);
			LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(0, px(58), 1);
			if (i > 0) cl.leftMargin = px(8);
			st.addView(c, cl);
		}
		info.addView(st);
		if (warns > 0) {
			// v2.142 (Viorel): in a box of its own, on one row.
			FitText wn = new FitText(ctx, 1, 0.4f);
			wn.setTypeface(reg);
			wn.setTextColor(ink());
			wn.setIncludeFontPadding(false);
			wn.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 14);
			wn.setGravity(Gravity.CENTER);
			wn.setText(warns == 1 ? Tr.t("There is 1 warning to look into.", "Este 1 avertizare de analizat.")
					: String.format(Tr.t("There are %d warnings to look into.", "Sunt %d avertizări de analizat."), warns));
			wn.setBackground(Glass.pill(Glass.disc(dark), 0, 12 * dp, dp));
			wn.setPadding(px(10), 0, px(10), 0);
			LinearLayout.LayoutParams wl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(40));
			wl.topMargin = px(8);
			wn.setLayoutParams(wl);
			info.addView(wn);
		}
		ScrollView sv = new ScrollView(ctx);
		sv.addView(info);
		body.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		// The buttons at the bottom of the screen (Viorel): See the log, Share, Save.
		String[] names = { Tr.t("See the log", "Vezi jurnalul"), Tr.t("Share", "Trimite"), Tr.t("Save", "Salvează") };
		String[] icons = { "⚙︎", "➚", "⤓" };
		for (int i = 0; i < 3; i++) {
			final int k = i;
			FitText bt = new FitText(ctx, 1, 0.5f);
			bt.setTypeface(bold);
			bt.setTextColor(i == 1 ? Theme.onPastel(dark) : ink());
			bt.setIncludeFontPadding(false);
			bt.setTextSize(17);
			bt.setText(icons[i] + "   " + names[i]);
			bt.setGravity(Gravity.CENTER);
			bt.setPadding(px(12), 0, px(12), 0);
			bt.setBackground(Glass.pill(i == 1 ? Theme.pastel(dark) : Glass.disc(dark), i == 1 ? 0 : Glass.edge(dark), 24 * dp, dp));
			bt.setOnClickListener(new View.OnClickListener() {
				public void onClick(View v) {
					if (k == 0) {
						Log.i(ctx, "JURNAL", "vezi jurnalul");
						new BackupPanel(ctx, dark, reg, bold, screen).lines(text);
					} else done.run(null, k);
				}
			});
			LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(50));
			bl.topMargin = px(10);
			col.addView(bt, bl);
		}
		show();
	}

	/**
	 * v2.140 (Viorel): the log's lines in a window of their own (‹ back to the Log): Toate, Erori,
	 * Azi, Ieri; a list that makes only the lines in sight (fast, also with Aa); the newest at the
	 * end; a long touch copies the line.
	 */
	private java.util.List<String> shownDay;
	private static final String fixedDay = Log.RESOLVED.replace("=", "").trim();

	/** Whether the row I shown is in the section of the fixed errors. */
	private boolean fixedRow(int i) {
		return shownDay != null && i < shownDay.size() && shownDay.get(i).equals(fixedDay);
	}

	void lines(String text) {
		backStyle = true;
		frame(Tr.t("The log's lines", "Liniile jurnalului"), null, null, null, null, null,
				Tr.t("Long touch on a line: copy it", "Atingere lungă pe o linie: o copiază"), true);
		final java.util.List<String> all = new java.util.ArrayList<String>(), day = new java.util.ArrayList<String>();
		String cur = "";
		for (String l : text.split("\n")) {
			if (l.startsWith("===== ")) cur = l.replace("=", "").trim();
			all.add(l);
			day.add(cur);
		}
		java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.ROOT);
		java.util.Calendar cal = java.util.Calendar.getInstance();
		final String today = f.format(cal.getTime());
		cal.add(java.util.Calendar.DAY_OF_YEAR, -1);
		final String yest = f.format(cal.getTime());
		final java.util.List<String> shown = new java.util.ArrayList<String>();
		shownDay = new java.util.ArrayList<String>();
		final java.util.List<String> sd = shownDay;
		final android.widget.ListView lv = new android.widget.ListView(ctx);
		lv.setDivider(null);
		lv.setFastScrollEnabled(true);
		lv.setBackground(Glass.cardBg(dark, 18 * dp, dp));
		lv.setPadding(px(10), px(8), px(10), px(8));
		lv.setClipToPadding(false);
		final android.widget.BaseAdapter ad = new android.widget.BaseAdapter() {
			public int getCount() {
				return shown.size();
			}

			public Object getItem(int i) {
				return shown.get(i);
			}

			public long getItemId(int i) {
				return i;
			}

			public View getView(int i, View v, ViewGroup g) {
				TextView t = v instanceof TextView ? (TextView) v : new TextView(ctx);
				String l = shown.get(i);
				boolean h = l.startsWith("=====");
				t.setTypeface(h ? bold : Typeface.MONOSPACE);
				char lv = Log.level(l);
				t.setTextColor(h ? accent() : lv == 'E' && !Log.network(l) && !fixedRow(i) ? 0xFFE0603F : lv == 'W' || lv == 'E' ? 0xFFD9A030 : ink());
				TextSize.size(t, h ? 12 : 11, ts);
				t.setPadding(0, px(h ? 10 : 3), 0, px(3));
				t.setText(h ? l.replace("=", "").trim() : l);
				return t;
			}
		};
		lv.setAdapter(ad);
		lv.setOnItemLongClickListener(new android.widget.AdapterView.OnItemLongClickListener() {
			public boolean onItemLongClick(android.widget.AdapterView<?> p, View v, int i, long id) {
				android.content.ClipboardManager cm = (android.content.ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
				if (cm != null) cm.setPrimaryClip(android.content.ClipData.newPlainText("log", shown.get(i)));
				android.widget.Toast.makeText(ctx, Tr.t("Line copied", "Linie copiată"), android.widget.Toast.LENGTH_SHORT).show();
				return true;
			}
		});
		// The filters.
		final String[] names = { Tr.t("All", "Toate"), Tr.t("Errors", "Erori"), Tr.t("Today", "Azi"), Tr.t("Yesterday", "Ieri") };
		final TextView[] chips = new TextView[4];
		LinearLayout fr = new LinearLayout(ctx);
		fr.setPadding(0, 0, 0, px(10));
		final Runnable[] pick = new Runnable[4];
		for (int i = 0; i < 4; i++) {
			final int k = i;
			chips[i] = text(14, false, ink());
			chips[i].setText(names[i]);
			chips[i].setGravity(Gravity.CENTER);
			chips[i].setPadding(px(14), px(7), px(14), px(7));
			pick[i] = new Runnable() {
				public void run() {
					shown.clear();
					sd.clear();
					for (int j = 0; j < all.size(); j++) {
						String l = all.get(j);
						boolean ok = k == 0 || (k == 1 ? Log.level(l) == 'E' && !Log.network(l) && !day.get(j).equals(fixedDay)
								: day.get(j).equals(k == 2 ? today : yest));
						if (ok) {
							shown.add(l);
							sd.add(day.get(j));
						}
					}
					for (int c = 0; c < 4; c++) {
						chips[c].setTextColor(c == k ? Theme.onPastel(dark) : ink());
						chips[c].setBackground(Glass.pill(c == k ? Theme.pastel(dark) : Glass.disc(dark), c == k ? 0 : Glass.edge(dark), 16 * dp, dp));
					}
					ad.notifyDataSetChanged();
					lv.setSelection(Math.max(0, shown.size() - 1));
				}
			};
			chips[i].setOnClickListener(new View.OnClickListener() {
				public void onClick(View v) {
					Log.i(ctx, "JURNAL", "filtru " + names[k]);
					pick[k].run();
				}
			});
			LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
			if (i > 0) cl.leftMargin = px(8);
			fr.addView(chips[i], cl);
		}
		body.addView(fr);
		body.addView(lv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
		pick[0].run();
		show();
	}

	private void show() {
		d.show();
	}

	/** A switch like the ⚙︎ ones. */
	private final class Switch extends View {
		boolean on;
		final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

		Switch(boolean on) {
			super(ctx);
			this.on = on;
		}

		@Override
		protected void onDraw(Canvas c) {
			float w = getWidth(), h = getHeight();
			p.setColor(on ? accent() : Theme.veil(dark, dark ? 0.2f : 0.16f));
			c.drawRoundRect(new RectF(0, 0, w, h), h / 2, h / 2, p);
			p.setColor(dark ? Theme.page(true) : 0xFFFFFFFF);
			c.drawCircle(on ? w - h / 2 : h / 2, h / 2, h / 2 - 3 * dp, p);
		}
	}

	/** A tick box. */
	private final class Box extends View {
		boolean on;
		final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

		Box(boolean on) {
			super(ctx);
			this.on = on;
		}

		@Override
		protected void onDraw(Canvas c) {
			float w = getWidth(), h = getHeight(), r = 5 * dp;
			RectF o = new RectF(dp, dp, w - dp, h - dp);
			if (on) {
				p.setStyle(Paint.Style.FILL);
				p.setColor(accent());
				c.drawRoundRect(o, r, r, p);
				p.setStyle(Paint.Style.STROKE);
				p.setStrokeWidth(2.4f * dp);
				p.setStrokeCap(Paint.Cap.ROUND);
				p.setStrokeJoin(Paint.Join.ROUND);
				p.setColor(0xFFFFFFFF);
				android.graphics.Path k = new android.graphics.Path();
				k.moveTo(w * 0.27f, h * 0.52f);
				k.lineTo(w * 0.44f, h * 0.68f);
				k.lineTo(w * 0.74f, h * 0.34f);
				c.drawPath(k, p);
			} else {
				p.setStyle(Paint.Style.STROKE);
				p.setStrokeWidth(1.8f * dp);
				p.setColor(dim());
				c.drawRoundRect(o, r, r, p);
			}
		}
	}
}
