//
// Fitter - model rail layout planning and documentation
//
// todo:
//      - draw lines between curves
//      - add means to start from scratch, insert 1st point
//      - complete undo/redo

import java.awt.*;
import java.io.*;
import java.net.*;
import java.util.*;
import javax.swing.*;


import java.awt.event.*;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseEvent;
import java.awt.geom.Arc2D;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;

import javax.imageio.ImageIO;

import javax.swing.*;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;


// ------------------------------------------------------------------------------
class Poly
{
    Poly    next;
    int     x;
    int     y;

    // -----------------------------------------------
    public Poly (int x, int y, Poly poly)  {
        this.x      = x;
        this.y      = y;
        if (null != poly)
            poly.next   = this;
    }
}

// ------------------------------------------------------------------------------
class Bench
{
    Bench           next;
    Poly            polyHead;
    Poly            poly;
    int             size = 0;

    // -----------------------------------------------
    public Bench (int x, int y)  {
        polyHead = poly = new Poly (x, y, poly);
        size = 1;

        // System.out.format ("\nBench new (%4d, %4d) size %3d\n", x, y, size);
    }

    // -----------------------------------------------
    public Bench add (int x, int y)  {
        if ( (1 < size) &&  (polyHead.x == poly.x) && (polyHead.y == poly.y) )
            return next = new Bench (x, y);

        poly = new Poly (x, y, poly);
        size++;

        // System.out.format ("Bench add (%4d, %4d) size %3d\n", x, y, size);
        return this;
    }

    // -----------------------------------------------
    public void dump ()  {
        System.out.format ("Bench.dump:\n");

        for (Bench bench = this; bench != null; bench = bench.next)  {
            for (Poly poly = bench.polyHead; poly != null; poly = poly.next)
                System.out.format ("  (%4d, %4d)\n", poly.x, poly.y);
            System.out.format ("\n");
        }
    }

    // -----------------------------------------------
    public Poly findPt (int x, int y, int max)  {
        Poly    poly    = null;
        double  dist    = 0;

        for (Bench bench = this; bench != null; bench = bench.next)  {
            for (Poly p= bench.polyHead; p!= null; p= p.next)  {
                double d = Math.hypot (x - p.x, y - p.y);

                if ((null == poly) || (d <= dist)) {
                    poly = p;
                    dist = d;
                }
            }
        }

        if (max >= dist)
            return poly;

        return null;
    }

    // -----------------------------------------------
    public void write (PrintWriter wr)  {
        for (Bench bench = this; bench != null; bench = bench.next)
            for (Poly poly = bench.polyHead; poly != null; poly = poly.next)
                wr.format("bench  %6d %6d\n", poly.x, poly.y);
        wr.format("\n");
    }

    // ------------------------------------------------------------------------
    //
    public void paintPath (Graphics2D g2d)  {
        // System.out.format ("Bench.paintPath:\n");

        for (Bench bench = this; bench != null; bench = bench.next)
            for (Poly poly = bench.polyHead; poly != null; poly = poly.next)  {
                // System.out.format ("  (%4d, %4d)\n", poly.x, poly.y);
                if (null != poly.next)
                    g2d.drawLine (poly.x, poly.y, poly.next.x, poly.next.y);
            }
    }

    // ------------------------------------------------------------------------
    //
    public void paintPoints (Graphics2D g2d, int dd)  {
        // System.out.format ("Bench.paintPoints:\n");

        for (Bench bench = this; bench != null; bench = bench.next)
            for (Poly p = bench.polyHead; p != null; p = p.next)  {
                // System.out.format ("  (%4d, %4d)\n", p.x, p.y);
                g2d.drawLine (p.x - dd, p.y - dd, p.x + dd, p.y + dd);
                g2d.drawLine (p.x - dd, p.y + dd, p.x + dd, p.y - dd);
            }
    }
}

// -----------------------------------------------------------------------------
// 
class Grid
{
    public static int size  = 0;

    int     x0;
    int     y0;
    int     x1;
    int     y1;
    int     spacing;

    // ---------------------------------------------------------
    //     y0Pt must be > y1Pt
    public Grid (int x0Pt, int y0Pt, int x1Pt, int y1Pt, int dd) {
        x0      = x0Pt;
        y0      = y0Pt;
        x1      = x1Pt;
        y1      = y1Pt;
        spacing = dd;

        size++;
    }

    // ---------------------------------------------------------
    void paint (Graphics2D g2d, Color color)  {
        g2d.setColor (color);

        for (int x = x0; x <= x1; x += spacing)
            g2d.drawLine (x, y0, x, y1);

        // y1 must be < y0
        for (int y = y1; y <= y0; y += spacing)
            g2d.drawLine (x0, y, x1, y);
    }
}

// -----------------------------------------------------------------------------
// 
class Track
{
    static  int   maxId  = 0;
    public  int   id;

    public  Point head;
    public  Point last;

    public  Track next;
    public  Track prev;
    
    // ---------------------------------------------------------
    public Track (Point hd, Point lst) {
        head  = hd;
        last  = lst;
        id    = ++maxId;

        System.out.format ("Track: ID %d\n", id);
    }

    // ---------------------------------------------------------
    public Track dup ()  {
     // if ((dbg & 2) != 0)
            System.out.format ("dup:\n");

        Point hd    = null;
        Point pt    = null;
        Point lst   = null;

        for (Point p = head; p != null; p = p.next)  {
            if (null == hd)
                pt = hd = new Point (p);
            else  {
                pt.next = new Point (p);
                pt      = pt.next;
            }
            lst = pt;
        }

        hd.connect();
        hd.initRadials();

        Track track = new Track (hd, lst);

        track.prev = this.prev;
        this.prev  = track;

        System.out.format ("dup: track prev ID %d\n", this.id);

        return track;
    }
}

// -----------------------------------------------------------------------------
// 
class Point
{
    static Point head  = null;
    static Point last  = null;
    static int   maxId = 0;

    static Track track = null;

    public static int   dbg   = 2;

    Point   next;
    int     x;
    int     y;
    int     id;

    String  label;

    Radial  radial;

    Point   pA;
    Point   pB;
    Point   pC;
    Point   pD;

    int     pAid;
    int     pCid;
    int     pBid;
    int     pDid;

    // ---------------------------------------------------------
    public void undo () {
        if (null != track.prev)  {
            track = track.prev;
            head  = track.head;
            last  = track.last;
            System.out.format ("undo: track ID %d\n", track.id);
        }
    }

    // ---------------------------------------------------------
    private void updateLast (Point p) {
        if (null == head)  {
            head = last = p;
        }
        else  {
            last.next = p;
            last      = p;
        }
    }

    // ---------------------------------------------------------
    public Point ()  {
        x           = 0;
        y           = 0;
    }

    // -------------------------------------
    public Point (Point p)  {
        x           = p.x;
        y           = p.y;
        id          = p.id;
     // radial      = new Radial ();

        pAid        = p.pAid;
        pBid        = p.pBid;
        pCid        = p.pCid;
        pDid        = p.pDid;

        if ((dbg & 1) != 0)
            System.out.format ("Point: (%d,%d)\n", x, y);
    }

    // -------------------------------------
    public Point (int xPt, int yPt, String s) {
        x           = xPt;
        y           = yPt;
        label       = s;
        radial      = new Radial ();
        id          = ++maxId;

        // System.out.format ("Point new: (%4d, %4d) %s\n", x, y, s);
        updateLast (this);

        if ((dbg & 1) != 0)
            System.out.format ("Point: (%4d, %4d) %3d\n", x, y, id);
    }

    // ---------------------------------------------------------
    private Point findId (int id)  {
        for (Point pt = head; pt != null; pt = pt.next)  {
            if (pt.id == id)
                return pt;
        }

        return null;
    }

    // ---------------------------------------------------------
    public void connect ()  {
        if ((dbg & 2) != 0)
            System.out.format ("connect:\n");

        for (Point pt = this; pt != null; pt = pt.next)  {

            pt.pA  = findId (pt.pAid);
            pt.pB  = findId (pt.pBid);

            pt.pC  = findId (pt.pCid );
            pt.pD  = findId (pt.pDid );

            if ((dbg & 1) != 0)
                pt.dispPts (" connect");
        }

        maxId = 100;
        for (Point pt = this; pt != null; pt = pt.next)
            pt.id = ++maxId;

        if (null == track)
            track = new Track (this, last);
    }

    // ---------------------------------------------------------
    public void write (PrintWriter wr)  {
        for (Point pt = head; pt != null; pt = pt.next)  {
            if (null != pt.pA)
                pt.pAid   = pt.pA.id;
            if (null != pt.pB)
                pt.pBid   = pt.pB.id;

            if (null != pt.pC )
                pt.pCid    = pt.pC .id;
            if (null != pt.pD )
                pt.pDid    = pt.pD .id;

            wr.format (" trk %4d %4d   %3d   %3d %3d  %3d %3d",
                pt.x, pt.y,   pt.id,
                pt.pAid, pt.pBid, pt.pCid , pt.pDid );

            wr.format ("    %3d", pt.id);
            wr.format ("\n");
        }
    }

    // ---------------------------------------------------------
    public Point delete ()  {
        Point  p0   = null;
        Point  p1   = null;

        if (null != pA)
            p0 = pA;

        if (null != pB)
            if (null == p0)
                p0 = pB;
            else if (null == p1)
                p1 = pB;

        if (null != pC)  {
            if (null == p0)
                p0 = pC;
            else if (null == p1)
                p1 = pC;
            else
                return this;        // error
        }

        if (null != pD)  {
            if (null == p0)
                p0 = pD;
            else if (null == p1)
                p1 = pD;
            else
                return this;        // error
        }

        System.out.format ("delete: [%3d]  [%3d]", id, p0.id);
        if (null != p1)
            System.out.format (" [%3d]", p1.id);
        System.out.format ("\n");

        p0.replace(this, p1);
        if (null != p1)
            p1.replace(this, p0);

        for (Point p = head; p != null; p = p.next)
            if (p.next == this)
                p.next = this.next;

        return null;
    }

    // ---------------------------------------------------------
    private double  dist (int xVal, int yVal)  {
        return Math.hypot (x - xVal, y - yVal);
    }

    public double  dist (Point p)  {
        return dist(p.x, p.y);
    }

    // ------------------------------------------------------------------------
    // find point nearest specified coordinates
    //     find nearest pt, assume this pt
    //     finally check if nearest is < DistThresh

    public Point findPt (int x, int y, int DistThresh) {
        Point   p0    = this;
        double  dist0 = dist(x, y);
        double  dist;
        Point   p1;

        for (Point p = head; p != null; p = p.next) {
            dist = p.dist(x, y);

            if (dist0 > dist)  {
                dist0 = dist;
                p0    = p;
            }
        }

        if (dist0 > DistThresh)
            return null;

        return p0;
    }

    // ---------------------------------------------------------
    // attach two point
    private void attach (Point p)  {
        if (null == pA)
            pA = p;
        else if (null == pB)
            pB = p;
        else if (null == pC)
            pC = p;
        else if (null == pD)
            pD = p;

        reCalc ();

        System.out.format ("attach: %3d", id);
        if (null != pA)
            System.out.format (", pA %3d", pA.id);
        if (null != pB)
            System.out.format (", pB %3d", pB.id);
        if (null != pC)
            System.out.format (", pC %3d", pC.id);
        if (null != pD)
            System.out.format (", pA %3d", pD.id);
        System.out.format ("\n");
    }

    // ---------------------------------------------------------
    // add  x,y, check if already a point
    public Point appendJoin (int x, int y, int distThresh)  {
        Point   p = findPt (x, y, distThresh);

        if (null == p)
            p = new Point (x, y, "");

        attach (p);
        p.attach (this);

        return p;
    }

    // ---------------------------------------------------------
    // replace old point with p in this point
    private Point replace (Point old, Point p)  {
        if (null == p)
            System.out.format ("replace: [%3d] [%3d] <- null\n", id, old.id);
        else
            System.out.format ("replace: [%3d] [%3d] <- [%3d]\n", id, old.id, p.id);

        if (old == pA)
            pA = p;
        else if (old == pB)
            pB = p;
        else if (old == pC)
            pC = p;
        else if (old == pD)
            pD = p;
        else  {
            System.out.format (
                "ERROR: replace - Point [%3d] not found in [%3d]\n", old.id, id);
            return null;
        }

        if (null != p)
            p.attach (this);

        return this;
    }

    // --------------------------------
    //  create new point at x,y and insert between twp points

    private Point insert (Point p0, Point p1, int x, int y)  {
        if ((dbg & 2) != 0)
            System.out.format (" insert: [%3d] %3d] (%4d, %4d)\n",
                p0.id, p1.id, x, y);

        track.dup ();

        Point  p = new Point (x, y, "");

        if (null != p1.replace (p0, p))
            if (null != p0.replace (p1, p))
                return p;

        return null;
    }

    // --------------------------------
    // locate segment closes to point

    private Point   seg0;
    private Point   seg1;
    private double  segK;

    private void bestSeg (Point p0, Point p1, int x, int y, boolean reset)  {
        double  d01 = p0.dist (p1);
        double  d0  = p0.dist (x, y);
        double  d1  = p1.dist (x, y);
        double  k   = d01 / (d0 + d1);  // <= 1, 1 is perfect

        if (reset || k > segK)  {
            seg0 = p0;
            seg1 = p1;
            segK = k;

            System.out.format ("bestSeg: [%3d] [%3d]\n", seg0.id, seg1.id);
        }
    }

    // --------------------------------
    // try to locate a segment the mouse lies on and insert new point

    public void findSeg (int x, int y)  {
        seg0 = seg1 = null;

        if ( (null == head) || (null == head.next) )
            return;

        bestSeg (head, head.next, x, y, true);     // some valid start value

        // search for closest segment 
        for (Point p = head; p != null; p = p.next) {
            if (null != p.pA)
                bestSeg (p, p.pA, x, y, false);

            if (null != p.pB)
                bestSeg (p, p.pB, x, y, false);

            if (null != p.pC)
                bestSeg (p, p.pC, x, y, false);

            if (null != p.pD)
                bestSeg (p, p.pD, x, y, false);
        }
    }

    // --------------------------------
    // try to locate a segment that mouse lies on and insert new point

    public Point tryInsert (int x, int y)  {
        System.out.format ("tryInsert: (%4d, %4d)\n", x, y);

        findSeg (x, y);

        if ( (null == seg0) || (null == seg1) )
            return null;

        return insert (seg0, seg1, x, y);
    }

    // --------------------------------
    // try to locate a segment the mouse lies on and insert new point

    public boolean breakLink (int x, int y)  {
        System.out.format ("breakLink: (%4d, %4d)\n", x, y);

        findSeg (x, y);

        if ( (null == seg0) || (null == seg1) )
            return false;

        if (null != seg0.replace (seg1, null))
            if (null != seg1.replace (seg0, null))
                return true;

        return false;
    }

    // ---------------------------------------------------------
    //
    public void paintMark (Graphics2D g2d, Color color)  {
        g2d.setColor (color);
        g2d.drawLine (x - 2, y - 2, x + 2, y + 2);
        g2d.drawLine (x - 2, y + 2, x + 2, y - 2);

     // if ("" != label)
     //     g2d.drawString (label, x, y);
     // else
            g2d.drawString (Integer.toString(id), x, y);
    }

    // ------------------------------------------------------------------------
    //
    private void  dispPt (Point pt) {
        if (null == pt)
            System.out.format ("%12s", "");
        else
            System.out.format (" [%3d] (%4d, %4d)",pt.id, pt.x, pt.y);
    }

    public  void  dispPts (String prefix) {
        System.out.format ("%s", prefix);
        dispPt (this);
        dispPt (pA);
        dispPt (pB);
        dispPt (pC);
        dispPt (pD);
        System.out.format ("\n");
    }

    // ------------------------------------------------------------------------
    //
    public void paintPath (Graphics2D g2d)  {
        // System.out.format ("paintPath:\n");

        for (Point pt = head; pt != null; pt = pt.next)  {
            // pt.dispPts (" paintPath");

            if (null != pt.pA)
                g2d.drawLine (pt.x, pt.y, pt.pA.x, pt.pA.y);

            if (null != pt.pB)
                g2d.drawLine (pt.x, pt.y, pt.pB.x, pt.pB.y);

            if (null != pt.pC)
                g2d.drawLine (pt.x, pt.y, pt.pC.x, pt.pC.y);

            if (null != pt.pD)
                g2d.drawLine (pt.x, pt.y, pt.pD.x, pt.pD.y);
        }
    }

    // ------------------------------------------------------------------------
    //
    public void paintPoints (Graphics2D g2d, Color color)  {
        for (Point pt = head; pt != null; pt = pt.next)
            pt.paintMark (g2d, color);
    }

    // ------------------------------------------------------------------------
    //
    public void paintRadials (Graphics2D g2d, Color color)  {
        g2d.setColor (color);
        for (Point pt = this; pt != null; pt = pt.next)
            pt.radial.paint (g2d, color);
    }

    // ------------------------------------------------------------------------
    //
    public int numConnects ()  {
        int   n = 0;

        if (null != pA)  n++;
        if (null != pB)  n++;
        if (null != pC)  n++;
        if (null != pD)  n++;

        return n;
    }

    // ------------------------------------------------------------------------
    //
    public void initRadials ()  {
        if ((dbg & 2) != 0)
            System.out.format ("initRadials:\n");

        for (Point pt = this; pt != null; pt = pt.next)  {
            if (2 != pt.numConnects())  {
             // System.out.format ("initRadials: numConnects  %d\n", numConnects());
                continue;
            }

            Point  p0 = pt.pA;
            Point  p1 = pt.pB;

                // this isn't quite right, but works on start up
            if (null == p0 && null != pt.pC )
                p0 = pt.pC;

            if (null == p1 && null != pt.pD )
                p1 = pt.pD;

            pt.radial = new Radial (pt, p0, p1);
        }
    }

    // ---------------------------------------------------------
    // 
    public void reCalc ()  {
        Point p0 = pA;
        if (null == p0)
            p0 = pC;

        Point p1 = pB;
        if (null == p1)
            p1 = pD;

        reCalc (p0, p1);
    }

    private void reCalc (Point a, Point b)  {
        // System.out.format ("reCalc: [%d]\n", id);

        if (a == null || b == null)  {
            System.out.format ("reCalc: warning id %d null args\n", id);
            return;
        }

        radial.calc (this, a, b);

        Point p0 = a.pA;
        if (null == p0)
            p0 = a.pC;

        Point p1 = a.pB;
        if (null == p1)
            p1 = a.pD;

        a.radial.calc (a, p0, p1);

        p0 = b.pA;
        if (null == p0)
            p0 = b.pC;

        p1 = b.pB;
        if (null == p1)
            p1 = b.pD;

        b.radial.calc (b, p0, p1);
    }
}

// -----------------------------------------------------------------------------
// 
class Radial
{
    static boolean  dbg = false;
    Point   p;
    Point   pU;
    Point   pV;

    Point   pA;
    Point   pB;
    Point   p0;

    int     rad;
    int     a1;
    int     a2;
    int     aExt;

    // ---------------------------------------------------------
    // 
    public Radial ()  {
        p   = new Point ();
        pU  = new Point ();
        pV  = new Point ();
        rad = 0;
    }

    public Radial (Point p0, Point pA, Point pB)  {
        p   = new Point ();
        pU  = new Point ();
        pV  = new Point ();

        this.p0 = p0;
        this.pA = pA;
        this.pB = pB;

        calc (p0, pA, pB);
    }

    // ---------------------------------------------------------
    private void paintConn (Point pt, Point pMid, Graphics2D g2d)  { 
        Point  p = null;

        if (null == pt || null == pMid)
            return;

        if (null == pt.radial || 0 == pt.radial.rad)  {
            g2d.drawLine (pMid.x, pMid.y, pt.x, pt.y);

            return;
        }
        if (null == pt.radial.pA)
            return;

        System.out.format ("paintConn:  [%3d] [%3d] [%3d]",
                    p0.id, pt.radial.pA.id, pt.radial.pB.id );

     // if (p0.id == pt.radial.pA.id)
        if (p0 == pt.radial.pA)
            p = pt.radial.pU;
     // else if (p0.id == pt.radial.pB.id)
        else if (p0 == pt.radial.pB)
            p = pt.radial.pV;

        if (null != p)  {
            int     x = this.p.x + rad;
            int     y = this.p.y + rad;

            g2d.drawLine (x-1, y-1, x+1, y+1);
            g2d.drawLine (x-1, y+1, x+1, y-1);

            g2d.drawLine (pMid.x, pMid.y, p.x, p.y);
            System.out.format (" (%4d, %4d) (%4d, %4d)",
                                    pMid.x, pMid.y, p.x, p.y);
            System.out.format (" <%3d> <%3d>", a1, aExt);
        }
        System.out.format (" \n");
    }

    // ---------------------------------------------------------
    public void paint (Graphics g, Color color)  {
        Graphics2D  g2d = (Graphics2D) g;

        if (rad == 0)
            return;

        if (false)
        System.out.format (
                    "Radial.paint: (%d, %d) [%d, %d] <%d, %d>\n",
            p.x, p.y, rad, rad, a1,  aExt );

        g2d.setColor (color);
        g.drawArc ( p.x, p.y, 2*rad, 2*rad, a1, aExt );

     // g2d.setColor (color.blue);
        paintConn (pA, pU, g2d);

     // g2d.setColor (color.red);
        paintConn (pB, pV, g2d);
    }

    // ---------------------------------------------------------
    // 
    public void calc (Point p0, Point pA, Point pB)  {
        // System.out.format ("Radial calc: [%d]\n", p0.id);

        if (pA == null || pB == null)  {
            if (dbg)
                System.out.format ("calc: warning id %d null args\n", p0.id);
            return;
        }

        if (dbg)
            System.out.format (" calc: %3d, %3d %3d\n",
                p0.id, pA.id, pB.id);

    //  if (p.pC  != null || p.pD  != null)
    //      return;

        pU.x     = pA.x;
        pU.y     = pA.y;

        pV.x     = pB.x;
        pV.y     = pB.y;

        pU.x = p0.x + ((pU.x - p0.x) / 2);
        pU.y = p0.y + ((pU.y - p0.y) / 2);

        pV.x = p0.x + ((pV.x - p0.x) / 2);
        pV.y = p0.y + ((pV.y - p0.y) / 2);

        if (dbg)
        System.out.format ("calc 0: p0 (%d, %d) pU (%d, %d) pV (%d, %d)\n",
            p0.x, p0.y, pU.x, pU.y, pV.x, pV.y);

        //  angle
        a1 = (int) Math.round(Math.atan2(p0.y-pU.y, pU.x-p0.x) * 180 /Math.PI);
        a2 = (int) Math.round(Math.atan2(p0.y-pV.y, pV.x-p0.x) * 180 /Math.PI);
        aExt   = a2 - a1;

        if (dbg)
        System.out.format ("calc 2: a1 %d, a2 %d, aExt %d\n", a1, a2, aExt);

        if (180 < Math.abs(aExt))  {
            aExt = Math.abs(aExt + 360);
            if (a1 < 0)
                a1 += 360;
            else
                a2 += 360;
        }

        if (dbg)
        System.out.format ("calc 3: a1 %d, a2 %d\n", a1, a2);

        //  find min length of adjacent pts
        double adj;
        double len1 = Math.hypot(pU.x-p0.x, pU.y-p0.y);
        double len2 = Math.hypot(pV.x-p0.x, pV.y-p0.y);

        if (len2 < len1)
            adj = len2;
        else
            adj = len1;

        if (dbg)
        System.out.format ("calc 4: len1 %8.2f, len2 %8.2f, adj %8.2f\n",
            len1, len2, adj);

        //  find pt along bisector
        int    x;
        int    y;
        double w  = (a1 + (aExt / 2)) *  Math.PI / 180;
        p.x = p0.x + (int)(adj * Math.cos(w));
        p.y = p0.y - (int)(adj * Math.sin(w));

        double len3 = Math.hypot(p.x-p0.x, p.y-p0.y);

        if (dbg)
        System.out.format ("calc 5: p (%d, %d), len3 %.1f\n", p.x, p.y, len3);

        //  locate radius center
        double c   =  Math.cos(0.5 * aExt *  Math.PI / 180);
        double hyp = adj / c;

        p.x   = p0.x + (int)( ((p.x-p0.x) / len3) * hyp);
        p.y   = p0.y + (int)( ((p.y-p0.y) / len3) * hyp);

        if (len2 < len1)
            rad   = (int) Math.hypot( p.x-pV.x, p.y-pV.y);
        else
            rad   = (int) Math.hypot( p.x-pU.x, p.y-pU.y);

        if (dbg)
        System.out.format ("calc 5: p (%d, %d), rad %d, hyp %.1f\n",
            p.x, p.y, rad, hyp);

        //  draw arc
        if (a1 < a2)  {
            a1  = a1 + 270;
            a2  = a2 + 90;
        }
        else {
            a2  = a2 + 270;
            a1  = a1 + 90;
        }
        aExt = a2 - a1;

        // locate ends of radial
        int    s = p.x + (int) (rad * Math.cos(a1 * Math.PI / 180));
        int    t = p.y - (int) (rad * Math.sin(a1 * Math.PI / 180));

        int    u = p.x + (int) (rad * Math.cos(a2 * Math.PI / 180));
        int    v = p.y - (int) (rad * Math.sin(a2 * Math.PI / 180));

        int a3 = (int) Math.round(Math.atan2(p0.y-pU.y, pU.x-p0.x) * 180 /Math.PI);
        int a4 = (int) Math.round(Math.atan2(p0.y- t,   s   -p0.x) * 180 /Math.PI);

        if (Math.abs(a3 - a4) < 5)  {
            pU.x     = s;
            pU.y     = t;

            pV.x     = u;
            pV.y     = v;
        }
        else {
            pU.x     = u;
            pU.y     = v;

            pV.x     = s;
            pV.y     = t;
        }

        // shift center to upper left corner of ellipse
        p.x -= rad;
        p.y -= rad;

        if (dbg)
        System.out.format ("calc 6: a1 %d, a2 %d\n", a1, a2);
    }
}

// -----------------------------------------------------------------------------
// 
class Text
{
    public static int size  = 0;

    String  text;
    int     x;
    int     y;
    int     font;
    int     color;
    Text    next;

    // ---------------------------------------------------------
    public Text (int xPt, int yPt, String s) {
        x       = xPt;
        y       = yPt;
        text    = s;

        size++;
    }
}

// -----------------------------------------------------------------------------
// -----------------------------------------------------------------------------
// 

public class Fitter extends JPanel
        implements KeyListener, MouseListener, MouseMotionListener,
                    ComponentListener
{
    JFrame   frame = new JFrame ();
    JPanel   panel;

    final int                   XMouseOffset = -4;
    final int                   YMouseOffset = -4;

    int                         zoom        = 0;
    double                      zoomSc      = 1.0;
    int                         xDispOff    = 0;
    int                         yDispOff    = 0;

    int                         scale       = 12;   // pixels per foot

    static int                  dbg         = 1;

    private String              title       = "Fitter Trackplanning Tool";

    int                         Width       = 800;
    int                         Height      = 400;

    String                      pthFilename = "sample";

    private final int           MAX_GRID    = 10;
    private Grid                grid []       = new Grid [MAX_GRID];

    private Point               trkHd;

    private Bench               bnch;
    private Bench               bnchHd       = null;

    private Text                txt;
    private Text                txtHd;

    final int                   ModeNormal   = 0;
    final int                   ModeAppend   = 1;
    final int                   ModeBreak    = 2;
    final int                   ModeEdit     = 3;
    final int                   ModeInsert   = 4;
    final int                   ModeBnch     = 5;
    final int                   ModeBnchEdit = 6;

    private int                 mode        = ModeNormal;

    private Point               ptCurr;
    private Point               ptHelp      = new Point ();;
    private Point               ptMouse     = new Point ();;

    private boolean             flagBorder  = true;
    private boolean             flagCurve   = true;
    private boolean             flagGrid    = true;
    private boolean             flagHelp    = false;
    private boolean             flagLine    = true;
    private boolean             flagPts     = true;
    private boolean             flagText    = true;

    private PrintWriter         wrOut       = new PrintWriter (System.out);


    //JMenuBar Variables
    JMenuBar    menuBar     = new JMenuBar  ();

    JMenu       file        = new JMenu     ("File");
    JMenuItem   openFile    = new JMenuItem ("Open");
    JMenuItem   saveFile    = new JMenuItem ("Save");
    JMenuItem   close       = new JMenuItem ("Close");

    JMenu       menuMode    = new JMenu     ("Mode");
    JMenuItem   edit        = new JMenuItem ("Edit");
    JMenuItem   bench       = new JMenuItem ("Bench");
    JMenuItem   track       = new JMenuItem ("Track");
    JMenuItem   layoutSize  = new JMenuItem ("Size");

    JLabel      appendLabel = new JLabel ("");
    JLabel      switchLabel = new JLabel ("");
    JLabel      modeLabel   = new JLabel ("");
    JLabel      xyLabel     = new JLabel ("");

    JTextField  textField   = new JTextField (10);

    // ---------------------------------------------------------
    //
    private void addMenus () {
        menuBar.add (file);
        file.add    (openFile);
        file.add    (saveFile);
        file.addSeparator ();
        file.add    (close);


        openFile.addActionListener(new ActionListener() {
            public void actionPerformed (ActionEvent e)  {
                System.out.format ("openFile:\n");

                String s = (String) JOptionPane.showInputDialog (
                            frame,
                            "Specify filename",
                            "Filename",
                            JOptionPane.PLAIN_MESSAGE );

                JOptionPane.showMessageDialog (frame, "open " + s);

                pthFilename = s;
            }
        });

        saveFile.addActionListener(new ActionListener() {
            public void actionPerformed (ActionEvent e)  {

                // String s = (String) 

                int yesNoCancel = 
                    JOptionPane.showInternalConfirmDialog (
                            frame,
                            "confirm save to:",
                            pthFilename,
                            JOptionPane.YES_NO_CANCEL_OPTION,
                            JOptionPane.INFORMATION_MESSAGE);


                if (JOptionPane.YES_OPTION == yesNoCancel)  {
                    System.out.format ("saveFile: %s\n", pthFilename);

                    try {
                        pthSave (pthFilename);
                    } catch (IOException ev)  {
                        System.err.format (
                            "Fitter: pthSave %s fault\n", pthFilename);
                    }
                }
            }
        });

        close.addActionListener(new ActionListener() {
            public void actionPerformed (ActionEvent e)  {
                Object [] opts = { "OK", "Cancel" };
                int n = JOptionPane.showOptionDialog (
                            frame,
                            "Close without saving file?",
                            "Close",
                            JOptionPane.OK_CANCEL_OPTION,
                            JOptionPane.QUESTION_MESSAGE,
                            null, opts, opts [1] );

                if (0 == n)
                    System.exit (0);
            }
        });


        // --------------------------------------
        // modes

        menuBar.add  (menuMode);
        menuMode.add (edit);
        menuMode.add (bench);
        menuMode.add (track);
        menuMode.addSeparator ();
        menuMode.add (layoutSize);


        edit.addActionListener(new ActionListener() {
            public void actionPerformed (ActionEvent e)  {
                System.out.format ("Edit");
                if (ModeBnch == mode)  {
                 // bnch    = bnch.pB;
                 // bnch.pA = null;
                }

                setMode (ModeNormal);
                ptCurr = null;
                repaint();
            }
        });

        bench.addActionListener(new ActionListener() {
            public void actionPerformed (ActionEvent e)  {
                System.out.format ("Bench");
                setMode (ModeBnch);
                ptCurr = null;
                repaint();
            }
        });

        track.addActionListener(new ActionListener() {
            public void actionPerformed (ActionEvent e)  {
                System.out.format ("Track");
                setMode (ModeInsert);
                repaint();
            }
        });

        layoutSize.addActionListener(new ActionListener() {
            public void actionPerformed (ActionEvent e)  {

                String s = (String) JOptionPane.showInputDialog (
                            frame,
                            "Specify layout dimensions: width x height",
                            "4x8");

                JOptionPane.showMessageDialog (frame, s);
            }
        });

        menuBar.add  (modeLabel);
        menuBar.add  (appendLabel);
        menuBar.add  (switchLabel);
        menuBar.add  (xyLabel);

     // menuBar.setMargin  (new Insets (0, 100, 10, 0));
     // menuBar.add  (textField);
    }

    // ------------------------------------------------------------------------
    // constructor loads the configuration files and
    //   determines the initial display geometry

    public Fitter (String[] args)
            throws IOException, IllegalArgumentException
    {
        // ------------------------------------------------
        // args
        int     i;
        for (i = 0; i < args.length; i++)  {
            if (args[i].startsWith("-"))  {
                String option = args [i].substring (1);
                   System.out.format (" Fitter: option %s\n", option);

                if (option.startsWith("D"))  {
                    if (option.length() > 1)
                        dbg = Integer.parseInt(option.substring(1));
                    else
                        dbg = 1;
                    System.out.format (" Fitter: dbg %d\n", dbg);
                }
            }
            else {
                break;
            }
        }

        if (i < args.length)
            pthFilename = args [i];

        setMode (ModeNormal);

        // ------------------------------------------------
        addMenus ();

        // ------------------------------------------------
        // original Fitter

        addKeyListener (this);
        addMouseListener (this);
        addMouseMotionListener (this);

        // ----------------------------------------------------
        // load layout description

        pthLoad (pthFilename);

            // deletes methods to auto create a .pth file

        // ----------------------------
        // configure panel

        panel = new JPanel () {
            @Override
            public void paintComponent (Graphics g) {
                // super.paintComponent (g);
                paintPanel (g);
            }
        };

        panel.setPreferredSize (new Dimension (Width, Height));

        // ----------------------------
        // initialize the frame

        frame.setTitle     (title);
        frame.setDefaultCloseOperation (JFrame.EXIT_ON_CLOSE);

        frame.setContentPane (this);
        frame.addComponentListener (this);

        frame.setLocation (300, 0);

        frame.add (panel);
        frame.setJMenuBar (menuBar);
        frame.pack ();
        frame.setVisible (true);

        frame.addWindowListener (new WindowAdapter()
        {
            public void windowClosing (WindowEvent e) {
                if (true)
                    JOptionPane.showMessageDialog (frame,
                        "save " + pthFilename);

                else
                    // int yesNoCancel = 
                    JOptionPane.showInternalConfirmDialog (
                            frame,
                            "confirm save to:",
                            pthFilename,
                            JOptionPane.YES_NO_CANCEL_OPTION,
                            JOptionPane.INFORMATION_MESSAGE);


                try {
                    pthSave (pthFilename);
                } catch (IOException ev)  {
                    System.err.format ("Fitter: pthSave %s fault\n",
                        pthFilename);
                }

                System.exit (0);
            }
        });
    }

    // ----------------------------------------------------
    // 
    public void componentResized (ComponentEvent e)
    {
        Rectangle r  = frame.getBounds ();
        Width        = r.width  - 26;
        Height       = r.height - 71;

        if ((dbg & 4) != 0)
            System.out.format ("componentResized: Width %d, Height %d\n",
                Width, Height); 

   //   System.out.format (
   //   "componentResized: wid %4d, ht %4d, wR %.2f, hR %.2f, sc %.2f\n",
   //       Width, Height, widRat, htRat, zoomSc);

        panel.setPreferredSize (new Dimension (Width, Height));
        repaint();
    }

    // -------------------------------------
    public void componentHidden (ComponentEvent e)
    {
        // System.out.format ("componenentHidden:\n");
    }

    // -------------------------------------
    public void componentMoved (ComponentEvent e)
    {
        // System.out.format ("componenentMoved:\n");
    }

    // -------------------------------------
    public void componentShown (ComponentEvent e)
    {
        // System.out.format ("componenentShown:\n");
    }

    // ------------------------------------------------------------------------
    // keys used with other keys (e.g. shift, ctl) or scroll keys
    private void offsetLimit ()
    {
        xDispOff = -ptMouse.x;
        yDispOff = -ptMouse.y;

        if (0 == zoom)  {
            xDispOff = 0;
            yDispOff = 0;
        }

        System.out.format ("offsetLimit: (%d; %d) zoom %d, zoomSc %f\n",
            xDispOff, yDispOff, zoom, zoomSc);
    }

    // ------------------------------------------------------------------------
    // keys used with other keys (e.g. shift, ctl) or scroll keys
    public void keyPressed (KeyEvent e)
    {
        int key   = e.getKeyCode();
        
        switch (key)  {
            case KeyEvent.VK_DOWN:
                yDispOff -= 100;
                offsetLimit ();
                break;

            case KeyEvent.VK_UP:
                yDispOff += 100;
                offsetLimit ();
                break;

            case KeyEvent.VK_LEFT:
                xDispOff += 100;
                offsetLimit ();
                break;

            case KeyEvent.VK_RIGHT:
                xDispOff -= 100;
                offsetLimit ();
                break;

            case KeyEvent.VK_CONTROL:
            case KeyEvent.VK_SHIFT:
                break;

            case KeyEvent.VK_INSERT:
                System.out.format("keyPressed: VK_INSERT\n");
                break;

            case KeyEvent.VK_DELETE:
                System.out.format("keyPressed: VK_DELETE\n");
                break;

            default:
                // may be picked up by keyTyped
                // System.out.format("keyPressed: %d\n", key);
                return;
        }

        repaint();
    }

    // ----------------------------------------------------
    public void keyReleased (KeyEvent e)
    {
        // ignore
    }

    String helpString = "  key commands:\n"
                      + "       ESC  - normal mode\n"
                      + "        a   - append mode\n"
                      + "        b   - border-insert\n"
                      + "        c   - paint curves\n"
                      + "        D   - display track points\n"
                      + "        d   - delete current point\n"
                      + "        e   - edit track\n"
                      + "        g   - paint grid\n"
                      + "        i   - insert track points\n"
                      + "        l   - paint lines\n"
                      + "        h   - display help\n"
                      + "        p   - paint points\n"
                      + "        P   - create .png file\n"
                      + "        S   - save to .pth file\n"
                      + "        Q   - quit\n"
                      + "        ?   - this help display\n"
                      + "       +/-  - zoom\n"
                      + " arrow-keys - pan screen when zoomed\n";

    // ----------------------------------------------------
    public void keyTyped (KeyEvent e)
    {
        byte[]  buf     = new byte [10];
        int     c       = e.getKeyChar();

        switch (c)  {
            case KeyEvent.VK_ESCAPE:
                setMode (ModeNormal);
                ptCurr     = null;
                break;

            case '+':
                zoom++;
                zoomSc *= 1.5;

                offsetLimit ();
                break;

            case '-':
                if (zoom > 0)  {
                    zoom--;
                    zoomSc /= 1.5;
                }
                offsetLimit ();
                break;

            case 'a':
                setMode (ModeAppend);
                break;

            case 'b':
                flagBorder = ! flagBorder;
                break;

            case 'B':
                setMode (ModeBreak);
                break;

            case 'c':
                flagCurve = ! flagCurve;
                break;

            case 'D':
                wrOut.format("Dump:\n");
                trkHd.write (wrOut);
                wrOut.flush();
                wrOut.format(" Dump:\n");
                break;

            case 'd':
                if (null == ptCurr)
                    message ("select point to delete");
                else {
                    ptCurr = ptCurr.delete ();
                    if (null != ptCurr)
                        message ("cannot delete turnout");
                }
                break;

            case 'e':
                setMode (ModeEdit);
                break;

            case 'g':
                flagGrid = ! flagGrid;
                break;

            case 'h':
                flagHelp = ! flagHelp;
                ptHelp.x = ptMouse.x;
                ptHelp.y = ptMouse.y;
                break;

            case 'i':
                setMode (ModeInsert);
                break;

            case 'l':
                flagLine = ! flagLine;
                break;

            case 'P':
                String filename = pthFilename + ".png";
                System.out.format ("create %s\n", filename);
                try {
                    BufferedImage bi =
                        new BufferedImage (
                            Width, Height, BufferedImage.TYPE_INT_ARGB);

                    Graphics2D ig2 = bi.createGraphics ();
                    paintPanel (ig2);

                    File outputfile = new File (filename);
                    ImageIO.write (bi, "png", outputfile);

                } catch (IOException ie)  {
                    ie.printStackTrace ();
                }
                break;

            case 'p':
                flagPts = ! flagPts;
                break;

            case 'Q':
                System.exit(0);
                break;

            case 'r':
                break;

            case 'S':
                try {
                    pthSave (pthFilename);
                } catch (IOException ev)  {
                    System.err.format ("Fitter: pthSave %s fault\n",
                        pthFilename);
                }
                break;

            case 'u':
                trkHd.undo ();
                ptCurr = null;
                break;

            case ' ':
            case '?':
                        System.out.format (helpString);

                        JOptionPane.showMessageDialog (null, helpString,
                            "Help Screen", JOptionPane.PLAIN_MESSAGE);

                System.out.format ("KeyTyped: help screen displayed\n");
                break;

            default:
                System.out.format ("keyTyped: default\n");
                System.out.format ("keyTyped: %d unexpected\n", c);
                break;
        }

        repaint();
    }

    // ------------------------------------------------------------------------
    // display help
    private void paintHelp (Graphics2D g2d)  {
        int fontSize = 14;
        g2d.setColor (Color.gray);
        g2d.setFont (new Font ("Arial", Font.PLAIN, fontSize));

     // System.out.format ("paintHelp: (%d; %d) helpString\n", ptHelp.x, ptHelp.y);

        String[]  fields = helpString.split("\n");

        for (int i = 0; i < fields.length; i++)
            g2d.drawString (fields [i], ptHelp.x, ptHelp.y + (i * fontSize));
    }

    // ------------------------------------------------------------------------
    private static boolean isInteger (String s)  {
        try {
            Integer.parseInt(s);
        } catch (NumberFormatException e)  {
            return false;
        }
        return true;
    }

    // ------------------------------------------------------------------------
    // main processes command line arguments settign the debug level and
    //   specifying the configuration filename
    //

    public static void main (String[] args)
            throws FileNotFoundException, IOException, IllegalArgumentException
    {
        if (false && args.length == 0) {
            throw new IllegalArgumentException("missing filename argument");
        }

        Fitter anim = new Fitter (args);

        // anim.startup();
    }

    // ------------------------------------------------------------------------
    // 

    private int xMousePos (MouseEvent e)
    {
        return (int) (e.getX() / zoomSc) + XMouseOffset - xDispOff;
    }

    private int yMousePos (MouseEvent e)
    {
        return (int) (e.getY() / zoomSc) + YMouseOffset - yDispOff;
    }

    // ------------------------------------------------------------------------
    // process mouse press, search for element closest to mouse location

    public void mousePressed (MouseEvent e)
    {
        int DistThresh   = 10;

        int  but = e.getButton();
        int  x   = xMousePos (e);
        int  y   = yMousePos (e);

        System.out.format (
            "mousePressed: %4d, %4d - mode %d, but %d, mode %d\n",
                x, y, mode, but, mode);

        // append to end of track or branch from existing track point
        if (mode == ModeAppend)  {
            if (null == ptCurr)  {
                setMode (ModeNormal);
                message ("must select point");
            }
            else
                ptCurr = ptCurr.appendJoin (x, y, DistThresh);
        }

        // search for track point
        else if (null != (ptCurr = trkHd.findPt (x, y, DistThresh)) )  {
            if ((dbg & 2) != 0)
                System.out.format ("mousePressed: search track point\n",
                        x, y);
        }

        // search for bench point
 //     else if (null != (ptCurr = bnchHd.findPt (x, y)) )  {
 //         if ((dbg & 2) != 0)
 //             System.out.format ("mousePressed: %d, %d - search bench point\n",
 //                     x, y);
 //         setMode (ModeBnchEdit);
 //     }

        // search for segment  AND ModeInsert
        else if ( (mode == ModeInsert) &&
                    (null != (ptCurr = trkHd.tryInsert (x, y))) ) { 
            if ((dbg & 2) != 0)
                System.out.format ("mousePressed: insert (%d, %d)\n", x, y);
            setMode (ModeNormal);
        }

        // check to break link
        else if ( (mode == ModeBreak) && trkHd.breakLink (x, y))  { 
            if ((dbg & 2) != 0)
                System.out.format ("mousePressed: break (%d, %d)\n", x, y);
            setMode (ModeNormal);
        }

        //
        else  {
            if ((dbg & 2) != 0)
                System.out.format ("mousePressed:\n");
        }

        repaint();
    }

    // ----------------------------------------------------
    //
    public void mouseClicked (MouseEvent e)
    {
        // System.out.format("mouseClicked:\n");
    }

    // ----------------------------------------------------
    //
    public void mouseDragged (MouseEvent e)
    {
        int  x   = xMousePos (e);
        int  y   = yMousePos (e);

        if ((dbg & 4) != 0)
        System.out.format ("mouseDragged: (%d, %d)\n", x, y);

        if (ptCurr == null || ModeNormal == mode)
            return;

        ptCurr.x     = x;
        ptCurr.y     = y;

        if (ModeBnchEdit != mode)
            ptCurr.reCalc ();

        repaint();
    }

    // ----------------------------------------------------
    //
    public void mouseMoved (MouseEvent e)
    {
        // System.out.format("mouseMoved:\n");
        int  x      = (int) e.getX();
        int  y      = (int) e.getY();

        if (ModeBnch == mode)  {
            ptMouse.x  = x;
            ptMouse.y  = y;

            repaint();
        }
        else {
            ptMouse.x  = x;
            ptMouse.y  = y;
        }

        xyLabel.setText( String.format(
            "%3d\"%2d\' x %3d\"%2d\' (%4d x %3d)",
            x/scale, x%scale, y/scale, y%scale, x, y));
    }

    // ----------------------------------------------------
    //
    public void mouseEntered (MouseEvent e)
    {
        requestFocusInWindow ();
        // System.out.format("mouseEntered:\n");
    }

    // ----------------------------------------------------
    //
    public void mouseExited (MouseEvent e)
    {
        // System.out.format("mouseExited:\n");
    }

    // ----------------------------------------------------
    public void mouseReleased (MouseEvent e)
    {
        int  x       = (int) e.getX();
        int  y       = (int) e.getY();

        // System.out.format ("mouseReleased: %d, %d\n", x, y);
        // repaint();
    }

    // ------------------------------------------------------------------------
    // redraw the screen
    // @Override
    public void paintPanel (Graphics g)
    {
        Graphics2D  g2d     = (Graphics2D) g;

        Color       backCol = Color.black;
        Color       bordCol = Color.green;
        Color       gridCol = new Color(20, 20, 20);
        Color       radCol  = Color.yellow;
        Color       trkCol  = Color.lightGray;

        // ----------------------------
        // zoom
        if ((dbg & 32) != 0)
            System.out.format ("paintPanel: (%d; %d) zoom %d, zoomSc %f\n",
                xDispOff, yDispOff, zoom, zoomSc);

            // needs work
            // x/yDispOff suggest an upper-left corner of a zoom area
            // but based on zoomSc, much of the screen may be blank
            // consider zoom area and scale based on corners identified
            //   by mouse press and release, but maintaining aspect

        g2d.scale (zoomSc, zoomSc);
        g2d.translate( xDispOff, yDispOff);

        // ----------------------------
        // clear background
        if (ModeBnch == mode)
            bordCol = Color.white;

        g2d.setColor (backCol);
        g2d.fillRect (0, 0, Width, Height);

        // ----------------------------
        if (flagGrid)
            for (int i = 0; i < Grid.size; i++)
                grid [i].paint (g2d, gridCol);

        if (flagBorder && bnchHd != null)  {
            g2d.setColor        (bordCol);
            bnchHd.paintPath    (g2d);
        }

        if (flagPts && null != bnchHd)  {
            g2d.setColor        (bordCol);
            bnchHd.paintPoints  (g2d, 2);
        }

        if (flagLine)  {
            g2d.setColor        (trkCol);
            trkHd.paintPath     (g2d);
        }

        if (flagPts)
            trkHd.paintPoints   (g2d, trkCol);

        if (flagCurve)
            trkHd.paintRadials  (g2d, radCol);

        if (null != ptCurr)
            if (ModeAppend == mode)
                ptCurr.paintMark    (g2d, Color.red);
            else if (ModeEdit == mode)
                ptCurr.paintMark    (g2d, Color.red);
            else
                ptCurr.paintMark    (g2d, Color.cyan);

        if (flagText)  {
            int fontSize = 20;
            g2d.setColor (Color.blue);
            g2d.setFont (new Font ("Arial", Font.PLAIN, fontSize));

            for (Text t = txt; t != null; t = t.next)
                g2d.drawString (t.text, t.x, t.y);
        }

        if (flagHelp)
            paintHelp (g2d);
    }

    // ------------------------------------------------------------------------
    // load a layout description file
    void pthLoad (String filename)
            throws FileNotFoundException, IOException
    {
        BufferedReader br;
        String         line;
        int            idx;
        String         lbl;
        Point          trk = null;

        pthFilename = filename;
        filename    = filename + ".pth";

        if ((dbg & 16) != 0)
            System.out.format("pthLoad: %s\n", filename);

        // should check for valid filename and open failure
        br = new BufferedReader(new FileReader(filename));

        while ((line = br.readLine()) != null)  {
            String[]    fields = line.split("  *");

            // skip first null field
            idx = 0;
            if (fields[idx].equals(""))  {
                if (1 == fields.length)
                    continue;       // blank line
                idx++;
            }

            // ---------------------------------------------
            // comment
            if (fields[idx].equals("#"))  {
                System.out.format (" comment - [%d] %s\n", fields.length, line);
            }

            // ---------------------------------------------
            // 
            else if (fields[idx].equals("end"))  {
                break;
            }

            // ---------------------------------------------
            // bench point
            else if (fields[idx].equals("bench"))  {
                if (fields.length <= (idx + 2))  {
                    System.err.format (
                        "pthLoad: bnch-pt requires 2 integer - %s\n", line);
                    System.exit(1);
                }

                int     x   = Integer.parseInt(fields[idx+1]);
                int     y   = Integer.parseInt(fields[idx+2]);

                if ((dbg & 16) != 0)
                    System.out.format ( " pthLoad: bnch (%4d, %4d)\n", x, y);

                if (null == bnchHd)
                    bnch = bnchHd = new Bench (x, y);
                else
                    bnch = bnch.add (x, y);
            }

            // ---------------------------------------------
            // 
            else if (fields[idx].equals("grid"))  {
                if (fields.length <= (idx + 5))  {
                    System.err.format (
                        "pthLoad: grid requires 4 intgeger values - %s\n", line);
                    System.exit(1);
                }

                int  i   = Grid.size;
                grid [i] = new Grid (   Integer.parseInt(fields[idx+1]),
                                        Integer.parseInt(fields[idx+2]),
                                        Integer.parseInt(fields[idx+3]),
                                        Integer.parseInt(fields[idx+4]),
                                        Integer.parseInt(fields[idx+5]) );
            }

            // ---------------------------------------------
            //
            else if (fields[idx].equals("height"))  {
                Height = Integer.parseInt(fields[idx+1]);
                if ((dbg & 16) != 0)
                    System.out.format ("pthLoad: Height %d\n", Height); 
            }

            // ---------------------------------------------
            // track point
            else if (fields[idx].equals("trk"))  {
                if (fields.length <= (idx + 2))  {
                    System.err.format (
                        "pthLoad: trk-pt requires 2 integer - %s\n", line);
                    System.exit(1);
                }

                if (fields.length < 9)  {
                    System.err.format ("pthLoad: requires 9 fields, %d\n", fields.length);
                    System.exit(1);
                }

                int x   = Integer.parseInt(fields[idx+1]);
                int y   = Integer.parseInt(fields[idx+2]);
                lbl     = fields[idx+8];

                if ((dbg & 16) != 0)
                    System.out.format ( " pthLoad: trk (%4d, %4d) %4s\n", x, y, lbl);

                if (trk == null)
                    trk = trkHd = new Point (x, y, lbl);
                else
                    trk = new Point (x, y, lbl);

                trk.id  = Integer.parseInt(fields[idx+3]);

                if (! fields[idx+4].equals("___"))
                    trk.pAid  = Integer.parseInt(fields[idx+4]);
                if (! fields[idx+5].equals("___"))
                    trk.pBid  = Integer.parseInt(fields[idx+5]);
                if (! fields[idx+6].equals("___"))
                    trk.pCid  = Integer.parseInt(fields[idx+6]);
                if (! fields[idx+7].equals("___"))
                    trk.pDid  = Integer.parseInt(fields[idx+7]);
            }

            // ---------------------------------------------
            //
            else if (fields[idx].equals("text"))  {
                if (fields.length <= (idx + 3))  {
                    System.err.format (
                        "pthLoad: name requires at least 3 argument- %s\n",
                        line);
                    System.exit(1);
                }

                int     x   = Integer.parseInt(fields[idx+1]);
                int     y   = Integer.parseInt(fields[idx+2]);
                int     i   = Text.size;

                String s = fields [idx+3];
                for (idx += 4; idx < fields.length; idx++)
                    s = s + " " + fields[idx];

                Text t = new Text (x, y, s);
                t.next = txt;
                txt    = t;

                if (null == txtHd)
                    txtHd = txt;
            }

            // ---------------------------------------------
            //
            else if (fields[idx].equals("width"))  {
                Width = Integer.parseInt(fields[idx+1]);
            }

            // ---------------------------------------------
            // unknown configuration file record
            else  {
                System.out.format (
                    " unknown - [%d] %s\n", fields.length, line);
            }
        }

        // bnchHd.dump ();

        // convert point IDs to pointers
        trkHd.connect();

        trkHd.initRadials();

        br.close();

        if ((dbg & 16) != 0)
            System.out.format (" pthLoad:\n");
    }

    // ------------------------------------------------------------------------
    // rename the old layout description file
    //

    void pthBackup (String filename)
            throws IOException
    {
        File    oldFile     = new File(filename);
        String  newFilePath = oldFile.getAbsolutePath() + "~";
        File    newFile     = new File(newFilePath);

        if (newFile.exists())  {
            if (! newFile.delete ())
                System.err.format ("Error: pthBackup failed to delete %s\n",
                    newFilePath);
        }

        if (! oldFile.renameTo (newFile))
            System.err.format ("Error: pthBackup failed to rename %s to %s\n",
                filename, newFilePath);
    }

    // ------------------------------------------------------------------------
    // save new layout description

    void pthSave (String filename)
            throws IOException
    {
        PrintWriter     wr;
        String          line;

        message ("save path file");

        System.out.format("pthSave: %s\n", filename);

        filename = filename + ".pth";
        pthBackup(filename);

        try {
            wr = new PrintWriter (filename, "UTF-8");
        } catch (FileNotFoundException e)  {
            System.err.format ("pthSave: Error opening %s\n", filename);
            return;
        }

        wr.format("height  %6d\n", Height);
        wr.format("width   %6d\n", Width);


        for (Text t = txt; t != null; t = t.next)
            wr.format("text  %6d %6d  %6s\n", t.x, t.y, t.text);
        wr.format("\n");

        for (int i = 0; i < Grid.size; i++) {
            wr.format("grid  %6d %6d  %6d %6d  %6d\n",
                grid[i].x0, grid[i].y0, grid[i].x1,
                grid[i].y1, grid[i].spacing );
        }
        wr.format("\n");

        if (null != bnchHd)  {
            bnchHd.write (wr);
            wr.format("\n");
        }

        trkHd.write (wr);
        wr.format("\n");

        wr.close();
    }

    // ------------------------------------------------------------------------
    // 
    void setMode (int value)
    {
        String  s;

        mode = value;
        switch (mode)  {
            case ModeNormal:
                s = "Normal";
                break;

            case ModeEdit:
                s = "Edit";
                break;

            case ModeAppend:
                s = "Append";
                break;

            case ModeBreak:
                s = "Break";
                break;

            case ModeInsert:
                s = "Insert";
                break;

            case ModeBnch:
                s = "Bench";
                break;

            case ModeBnchEdit:
                s = "Bench-Edit";
                break;

            default:
                s = "unknown";
                break;
        }

        if ((dbg & 2) != 0)
            System.err.format ("setMode: %d %s\n", value, s);

        modeLabel.setText(String.format("%-12s", s));
    }

    // --------------------------------
    private void message (String msg)
    {
        JOptionPane.showMessageDialog (null, msg,
                    "Warning", JOptionPane.PLAIN_MESSAGE);
    }

}
