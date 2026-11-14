package ro.meteoalarm.android;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

/**
 * v5.00 (Viorel's reference picture): plain line icons for the new Settings and the windows,
 * drawn on a 24-unit grid, in one colour.
 */
final class LineIcon extends Drawable {
	private final String name;
	private final int color;
	private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

	LineIcon(String name, int color) {
		this.name = name;
		this.color = color;
	}

	@Override
	public void draw(Canvas c) {
		android.graphics.Rect b = getBounds();
		float s = Math.min(b.width(), b.height()) / 24f;
		c.save();
		c.translate(b.exactCenterX() - 12 * s, b.exactCenterY() - 12 * s);
		c.scale(s, s);
		draw(c, p, name, color);
		c.restore();
	}

	/** Draws icon NAME in a 24×24 box at the origin. */
	static void draw(Canvas c, Paint p, String name, int color) {
		p.setColor(color);
		p.setStyle(Paint.Style.STROKE);
		p.setStrokeWidth(1.8f);
		p.setStrokeCap(Paint.Cap.ROUND);
		p.setStrokeJoin(Paint.Join.ROUND);
		Path h = new Path();
		switch (name) {
		case "map":
			h.moveTo(3, 6); h.lineTo(9, 3.5f); h.lineTo(15, 6); h.lineTo(21, 3.5f); h.lineTo(21, 18); h.lineTo(15, 20.5f); h.lineTo(9, 18); h.lineTo(3, 20.5f); h.close();
			h.moveTo(9, 3.5f); h.lineTo(9, 18); h.moveTo(15, 6); h.lineTo(15, 20.5f);
			break;
		case "farm":
			h.moveTo(4, 20); h.lineTo(20, 20);
			h.moveTo(12, 20); h.lineTo(12, 11);
			h.moveTo(12, 13); h.cubicTo(12, 8, 8, 7, 5, 7); h.cubicTo(5, 11, 8, 13, 12, 13);
			h.moveTo(12, 11); h.cubicTo(12, 6, 16, 4, 19, 4); h.cubicTo(19, 8, 16, 11, 12, 11);
			break;
		case "thermo":
			h.addRoundRect(new RectF(9.5f, 2.5f, 14.5f, 15), 2.5f, 2.5f, Path.Direction.CW);
			c.drawPath(h, p);
			c.drawCircle(12, 18, 3.5f, p);
			h = new Path();
			h.moveTo(12, 8); h.lineTo(12, 16);
			break;
		case "clock":
			c.drawCircle(12, 12, 9, p);
			h.moveTo(12, 7); h.lineTo(12, 12); h.lineTo(15.5f, 14);
			break;
		case "palette":
			h.moveTo(12, 3);
			h.cubicTo(6.5f, 3, 3, 7, 3, 12); h.cubicTo(3, 17, 7, 21, 12, 21);
			h.cubicTo(13.5f, 21, 14, 20, 13.5f, 18.5f); h.cubicTo(13, 17, 14, 15.5f, 15.5f, 15.5f);
			h.lineTo(17.5f, 15.5f); h.cubicTo(19.5f, 15.5f, 21, 14, 21, 12); h.cubicTo(21, 7, 17, 3, 12, 3);
			c.drawPath(h, p);
			p.setStyle(Paint.Style.FILL);
			c.drawCircle(7.5f, 11.5f, 1.3f, p);
			c.drawCircle(10, 7.5f, 1.3f, p);
			c.drawCircle(14.5f, 7.5f, 1.3f, p);
			c.drawCircle(17, 11, 1.3f, p);
			return;
		case "globe":
			c.drawCircle(12, 12, 9, p);
			h.moveTo(3, 12); h.lineTo(21, 12);
			h.addOval(new RectF(8, 3, 16, 21), Path.Direction.CW);
			break;
		case "locate":
			c.drawCircle(12, 12, 6.5f, p);
			p.setStyle(Paint.Style.FILL);
			c.drawCircle(12, 12, 2.2f, p);
			p.setStyle(Paint.Style.STROKE);
			h.moveTo(12, 2); h.lineTo(12, 5.5f); h.moveTo(12, 18.5f); h.lineTo(12, 22);
			h.moveTo(2, 12); h.lineTo(5.5f, 12); h.moveTo(18.5f, 12); h.lineTo(22, 12);
			break;
		case "quake":
			h.moveTo(2, 12); h.lineTo(6, 12); h.lineTo(8, 8); h.lineTo(11, 17); h.lineTo(14, 5); h.lineTo(16.5f, 14); h.lineTo(18, 12); h.lineTo(22, 12);
			break;
		case "list":
			for (int i = 0; i < 3; i++) {
				float y = 7 + i * 5;
				h.moveTo(9, y); h.lineTo(20, y);
				c.drawPoint(4.5f, y, p);
			}
			p.setStrokeWidth(2.6f);
			for (int i = 0; i < 3; i++) c.drawPoint(4.5f, 7 + i * 5, p);
			p.setStrokeWidth(1.8f);
			break;
		case "tornado":
			h.moveTo(3, 5); h.lineTo(21, 5); h.moveTo(5, 9); h.lineTo(19, 9); h.moveTo(7.5f, 13); h.lineTo(16, 13); h.moveTo(10, 17); h.lineTo(14, 17); h.moveTo(11.5f, 20.5f); h.lineTo(12.5f, 20.5f);
			break;
		case "alert":
			h.moveTo(12, 9); h.lineTo(12, 3); h.moveTo(12, 9); h.lineTo(7, 20); h.moveTo(12, 9); h.lineTo(17, 20); h.moveTo(12, 9); h.lineTo(12, 18);
			h.moveTo(5, 8); h.lineTo(8, 9.5f); h.moveTo(19, 8); h.lineTo(16, 9.5f); h.moveTo(4, 14); h.lineTo(7, 13.5f); h.moveTo(20, 14); h.lineTo(17, 13.5f);
			break;
		case "bell":
			h.moveTo(6, 16); h.lineTo(6, 11); h.cubicTo(6, 7, 8.5f, 4.5f, 12, 4.5f); h.cubicTo(15.5f, 4.5f, 18, 7, 18, 11); h.lineTo(18, 16); h.lineTo(19.5f, 17.5f); h.lineTo(4.5f, 17.5f); h.close();
			h.moveTo(10, 20.5f); h.lineTo(14, 20.5f);
			break;
		case "moon":
			h.moveTo(20, 14.5f); h.cubicTo(18.5f, 15.3f, 17, 15.6f, 15.5f, 15.6f); h.cubicTo(11, 15.6f, 8.4f, 13, 8.4f, 8.5f); h.cubicTo(8.4f, 7, 8.7f, 5.5f, 9.5f, 4);
			h.cubicTo(5.8f, 5.2f, 3.5f, 8.5f, 3.5f, 12.2f); h.cubicTo(3.5f, 17, 7.2f, 20.5f, 12, 20.5f); h.cubicTo(15.7f, 20.5f, 18.8f, 18.2f, 20, 14.5f); h.close();
			break;
		case "sun":
			c.drawCircle(12, 12, 4, p);
			for (int i = 0; i < 8; i++) {
				double a = Math.PI / 4 * i;
				h.moveTo(12 + 7 * (float) Math.cos(a), 12 + 7 * (float) Math.sin(a));
				h.lineTo(12 + 9.5f * (float) Math.cos(a), 12 + 9.5f * (float) Math.sin(a));
			}
			break;
		case "flask":
			h.moveTo(9.5f, 3); h.lineTo(14.5f, 3); h.moveTo(10, 3); h.lineTo(10, 9); h.lineTo(4.5f, 19); h.cubicTo(4, 20, 4.5f, 21, 5.5f, 21); h.lineTo(18.5f, 21);
			h.cubicTo(19.5f, 21, 20, 20, 19.5f, 19); h.lineTo(14, 9); h.lineTo(14, 3);
			h.moveTo(7.5f, 15); h.lineTo(16.5f, 15);
			break;
		case "widget":
			h.addRoundRect(new RectF(3.5f, 3.5f, 11, 11), 2.2f, 2.2f, Path.Direction.CW);
			h.addRoundRect(new RectF(13, 3.5f, 20.5f, 11), 2.2f, 2.2f, Path.Direction.CW);
			h.addRoundRect(new RectF(3.5f, 13, 20.5f, 20.5f), 2.2f, 2.2f, Path.Direction.CW);
			break;
		case "download":
			h.moveTo(12, 3); h.lineTo(12, 15); h.moveTo(7, 10); h.lineTo(12, 15); h.lineTo(17, 10);
			h.moveTo(4, 17); h.lineTo(4, 20); h.lineTo(20, 20); h.lineTo(20, 17);
			break;
		case "upload":
			h.moveTo(12, 15); h.lineTo(12, 3); h.moveTo(7, 8); h.lineTo(12, 3); h.lineTo(17, 8);
			h.moveTo(4, 17); h.lineTo(4, 20); h.lineTo(20, 20); h.lineTo(20, 17);
			break;
		case "log":
			h.addRoundRect(new RectF(5, 3, 19, 21), 2.5f, 2.5f, Path.Direction.CW);
			h.moveTo(8.5f, 8); h.lineTo(15.5f, 8); h.moveTo(8.5f, 12); h.lineTo(15.5f, 12); h.moveTo(8.5f, 16); h.lineTo(13, 16);
			break;
		case "sync":
			h.addArc(new RectF(4, 4, 20, 20), 200, 130);
			h.moveTo(18.5f, 3.5f); h.lineTo(19.2f, 8.4f); h.lineTo(14.4f, 8.6f);
			h.addArc(new RectF(4, 4, 20, 20), 20, 130);
			h.moveTo(5.5f, 20.5f); h.lineTo(4.8f, 15.6f); h.lineTo(9.6f, 15.4f);
			break;
		case "plane":
			h.moveTo(21, 3); h.lineTo(3, 10.5f); h.lineTo(10, 13.5f); h.lineTo(13.5f, 21); h.close();
			h.moveTo(21, 3); h.lineTo(10, 13.5f);
			break;
		case "help":
			c.drawCircle(12, 12, 9, p);
			h.moveTo(9.3f, 9.3f); h.cubicTo(9.6f, 7.8f, 10.7f, 7, 12, 7); h.cubicTo(13.6f, 7, 14.8f, 8.1f, 14.8f, 9.5f); h.cubicTo(14.8f, 11.5f, 12, 11.6f, 12, 13.8f);
			c.drawPath(h, p);
			p.setStyle(Paint.Style.FILL);
			c.drawCircle(12, 16.9f, 1.15f, p);
			return;
		case "info":
			c.drawCircle(12, 12, 9, p);
			h.moveTo(12, 11); h.lineTo(12, 16.5f);
			c.drawPath(h, p);
			p.setStyle(Paint.Style.FILL);
			c.drawCircle(12, 7.6f, 1.15f, p);
			return;
		case "star":
		case "star-off":
			for (int i = 0; i < 10; i++) {
				double a = -Math.PI / 2 + Math.PI / 5 * i;
				float r = i % 2 == 0 ? 9.5f : 4.2f, x = 12 + r * (float) Math.cos(a), y = 12.8f + r * (float) Math.sin(a);
				if (i == 0) h.moveTo(x, y);
				else h.lineTo(x, y);
			}
			h.close();
			if (name.equals("star")) p.setStyle(Paint.Style.FILL_AND_STROKE);
			break;
		case "chevron":
			h.moveTo(9, 5.5f); h.lineTo(15.5f, 12); h.lineTo(9, 18.5f);
			break;
		case "plus":
			h.moveTo(12, 5); h.lineTo(12, 19); h.moveTo(5, 12); h.lineTo(19, 12);
			break;
		case "screen":
			h.addRoundRect(new RectF(6.5f, 2.5f, 17.5f, 21.5f), 2.5f, 2.5f, Path.Direction.CW);
			h.moveTo(10.5f, 18.5f); h.lineTo(13.5f, 18.5f);
			break;
		case "parcel":
			h.moveTo(3, 8); h.lineTo(12, 4); h.lineTo(21, 8); h.lineTo(12, 12); h.close();
			h.moveTo(3, 12.5f); h.lineTo(12, 16.5f); h.lineTo(21, 12.5f);
			h.moveTo(3, 17); h.lineTo(12, 21); h.lineTo(21, 17);
			break;
		default:
			c.drawCircle(12, 12, 8, p);
			return;
		}
		c.drawPath(h, p);
	}

	@Override
	public void setAlpha(int a) {
	}

	@Override
	public void setColorFilter(ColorFilter f) {
	}

	@Override
	public int getOpacity() {
		return PixelFormat.TRANSLUCENT;
	}
}
