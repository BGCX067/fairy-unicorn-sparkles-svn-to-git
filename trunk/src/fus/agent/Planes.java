/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package fus.agent;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.GridLayout;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import se.sics.tac.aw.*;

/**
 * The module of the Fairy Unicorn Sparkles TAC Classic agent that handles the auctions for rooms in the hotels.
 * @author David Monks, dm11g08@ecs.soton.ac.uk
 */
public class Planes {
    //CLASS - SINGLETON PATTERN
    private static Planes singleton;
    
    public static Planes getPlanesHandler(FUSAgent impl){
        if (singleton == null){
            return singleton = new Planes(impl);
        }else{
            return singleton;
        }
    }

    public class XDistribution {
        public final int samples = 101;
        public double[] point;
        public double[] distribution;

        public XDistribution() {
            point = new double[samples];
            distribution = new double[samples];
            double interval = 40.0/(samples-1);

            for (int i=0; i<samples; i++) {
                point[i] = -10.0 + i * interval;
                distribution[i] = 1.0/samples;
            }
        }

        public XDistribution (XDistribution x) {
        distribution = new double[x.distribution.length];
        point = new double[x.point.length];
        for (int i = 0; i < x.distribution.length; i++) {
            distribution[i] = x.distribution[i];
        }
        for (int i = 0; i < x.point.length; i++) {
            point[i] = x.point[i];
        }
        }
    }
    
    //INSTANCE
    
    protected final FUSAgent impl;
    protected float[][] priceHistory;
    protected float[] currentPrice;
    protected XDistribution[] dist;

    //GUI STUFF
    protected JFrame frame;
    protected DrawPrices[] drawPrices;
    protected DrawXDist[] drawDists;

    static class DrawPrices extends JPanel {
        protected float[] priceHistory;
        protected int boughtAt;
        protected float boughtFor;
        protected float minPrice;

        protected int allocation;

        public DrawPrices(int alloc) {
            priceHistory = new float[55];
            for(int t=0; t<55; t++)
                priceHistory[t]=0;

            boughtAt = -1;
            boughtFor = 0;
            minPrice = 0;

            allocation = alloc;
        }

        public void setAllocation(int alloc) {
            allocation = alloc;
            repaint();
        }

        public void setPrice(float p, int t) {
            priceHistory[t] = p;

            repaint();
        }
        public void setBought(float p, int t) {
            boughtAt = t;
            boughtFor = p;
        }
        public void setMinPrice(float p) {
            minPrice = p;
        }

        public void clear() {
            for(int t=0; t<55; t++)
                priceHistory[t]=0;

            boughtAt = -1;
            boughtFor = 0;
            allocation = 0;
            minPrice = 0;
            repaint();
        }

        public void paintComponent(Graphics g) {
            super.paintComponent(g);

            if(allocation == 0) {
                g.setColor(new Color(0,0,0,0.5f));
                g.fillRect(0, 0, this.getWidth(), this.getHeight());
            }
            g.drawChars(Integer.toString(allocation).toCharArray(), 0, 1, 10, 15);

            g.setColor(Color.BLACK);
            g.drawRect(1,1,this.getWidth()-2,this.getHeight()-2);
            
            g.setColor(Color.RED);
            float tInterval = this.getWidth() / 54.f;
            float pInterval = this.getHeight() / (800.f - 150.f);

            for(int t=0; t<54; t++) {
                if(priceHistory[t+1] == 0)
                    continue;
                g.setColor(Color.RED);
                g.drawLine((int)(t*tInterval), this.getHeight() - (int)((priceHistory[t]-150.f)*pInterval), (int)((t+1)*tInterval), this.getHeight() - (int)((priceHistory[t+1]-150.f)*pInterval));
                
                if(t == boughtAt) {
                    g.setColor(Color.BLUE);
                    g.drawLine(0, this.getHeight() - (int)((boughtFor-150.f)*pInterval), this.getWidth(), this.getHeight() - (int)((boughtFor-150.f)*pInterval));
                    g.drawLine((int)(t*tInterval), 0, (int)((t)*tInterval), this.getHeight());
                }
            }
            g.setColor(Color.GREEN);
            g.drawLine(0, this.getHeight() - (int)((minPrice-150.f)*pInterval), this.getWidth(), this.getHeight() - (int)((minPrice-150.f)*pInterval));
        }
    }

    static class DrawXDist extends JPanel {
        protected XDistribution distribution;

        public DrawXDist(XDistribution dist) {
            distribution = dist;
        }

        public void setDist(XDistribution dist) {
            distribution = dist;
        }

        public void paintComponent(Graphics g) {
            super.paintComponent(g);

            g.setColor(Color.BLACK);
            g.drawRect(1,1,this.getWidth()-2,this.getHeight()-2);

            double xInterval = this.getWidth() / distribution.samples;

            double maxP = -1;
            for(int x=0; x<distribution.samples-1; x++) {
                if(distribution.distribution[x] > maxP)
                    maxP = distribution.distribution[x];
            }
            double pInterval = this.getHeight() / maxP;

            for(int x=0; x<distribution.samples-1; x++) {
                g.setColor(Color.BLUE);
                g.drawLine((int)(x*xInterval), this.getHeight() - (int)(distribution.distribution[x]*pInterval), (int)((x+1)*xInterval), this.getHeight() - (int)(distribution.distribution[x+1]*pInterval));
            }
        }
    }
    
    private Planes(FUSAgent i){
        this.impl = i;
        this.priceHistory = new float[8][54];
        this.currentPrice = new float[8];

        dist = new XDistribution[8];
        for (int j = 0; j < 8; j++) {
            dist[j] = new XDistribution();
        }

        //set up GUI
        frame = new JFrame();
        drawPrices = new DrawPrices[8];
        drawDists = new DrawXDist[8];
        
        frame.setLayout(new GridLayout(9,2));

	frame.add(new JLabel("<HTML>plane price history:<BR/><FONT COLOR='FF0000'>Red</FONT> - Current Price<BR/><FONT COLOR='0000FF'>Blue</FONT> - Bought Price<BR/><FONT COLOR='00FF00'>Green</FONT> - Expected Minimum"));
        frame.add(new JLabel("x distribution:"));

        for(int auction=0; auction<8; auction++) {
            drawPrices[auction] = new DrawPrices(impl.getAgent().getAllocation(auction));
            drawDists[auction] = new DrawXDist(dist[auction]);
            frame.add(drawPrices[auction]);
            frame.add(drawDists[auction]);
        }

        //frame.setSize(100, 900);
        frame.setVisible(true);
    }

    public void initialize() {
        //reset the distributions
        for (int j = 0; j < 8; j++) {
            dist[j] = new XDistribution();
        }

        //reset the graphs
        for(int auction=0; auction<8; auction++) {
            drawPrices[auction].clear();
            drawDists[auction].setDist(dist[auction]);
        }
    }

    public void finalize() {
        
    }

    public float initialBid(int auction){
        //cant do much on the initial bid
        return 0;
    }
    public void quoteUpdated(Quote q) {
        int auction = q.getAuction();
        int decisec = impl.getGameDecisec();

        setPrice(auction, decisec, (int) q.getAskPrice());

        drawPrices[auction].setAllocation(impl.getAgent().getAllocation(auction));
        drawPrices[auction].setPrice(q.getAskPrice(), decisec);
        drawPrices[auction].setMinPrice(this.getExpectedMinPrice(auction));
        drawDists[auction].repaint();

        if(buyNow(auction, decisec)) {
            int alloc = impl.getAgent().getAllocation(auction);
            int owned = impl.getAgent().getOwn(auction);
            int want = alloc-owned;
            if(want > 0) {
                Bid bid = new Bid(auction);
                float bidPrice = q.getAskPrice() + 10;

                bid.addBidPoint(want, bidPrice);

                impl.getAgent().submitBid(bid);
            }
        }
    }

    public void ticketBooked(int auction, int quantity, float price) {
        impl.setPrice(auction, price);
        drawPrices[auction].setBought(price, impl.getGameDecisec());
    }

    public void setPrice (int auctionNo, int time, int price) {
        this.priceHistory[auctionNo][time] = price;
        this.currentPrice[auctionNo] = price;

        if (time == 0) return;
        if (priceHistory[auctionNo][time-1] == 0) return;

        updateXDist(auctionNo, time);
    }

    private void updateXDist (int auctionNo, int time) {
        for (int i=0; i<dist[auctionNo].samples; i++) {
            double x = dist[auctionNo].point[i];
            int diff = (int)priceHistory[auctionNo][time] - (int)priceHistory[auctionNo][time-1];
            dist[auctionNo].distribution[i] *= probabilityY(x, diff, time);
        }

        double sum = 0;
        for (int i=0; i<dist[auctionNo].samples; i++)
            sum += dist[auctionNo].distribution[i];

        if (sum == 0) {
            dist[auctionNo] = new XDistribution();
        }
        else {
            for (int i = 0; i < dist[auctionNo].samples; i++) {
                dist[auctionNo].distribution[i] /= sum;
            }
        }
    }

    public boolean buyNow (int auction, int time) {
        if(time<2) return false;
        if(time>=52) return true;

        double expected_perturbation = 0;

        for (int i = 0; i < dist[auction].samples; i++) {
            double x = dist[auction].point[i];
            double p = dist[auction].distribution[i];
            expected_perturbation += p * getExpectedPerturbation((int)this.currentPrice[auction], x, time);
        }

        if (expected_perturbation > 0) return true;
        else return false;
    }
    private double getExpectedPerturbation(int price, double x, int time) {
        double maxChange = 10;
        double minChange = -10;

        int change = (int) Math.abs(x(time, x));

        if (change > 0) maxChange = change;
        if (change < 0)	minChange = change;

        minChange = Math.max(150 - price, minChange);
        maxChange = Math.min(800.0 - price, maxChange);

        return (maxChange + minChange) / 2.0;
    }

    public float getExpectedMinPrice (int auctionNo) {
        double ret = 0;
        int price = (int)Math.floor(currentPrice[auctionNo]);
        int time = impl.getGameDecisec();

        for (int i = 0; i < dist[auctionNo].samples; i++) {
            double x = dist[auctionNo].point[i];
            double p = dist[auctionNo].distribution[i];
            double expPrice = price + getExpectedMinPriceChange(price, x, time);

            ret += expPrice * p;
        }

        return (float) ret;
    }

    private double getExpectedMinPriceChange (int price, double x, int time) {
        double expectedChange = 0;
        double expectedMinChange = Double.POSITIVE_INFINITY;
        int currPrice = price;

        for (int t = time; t < 54; t++) {
            expectedChange += getExpectedPerturbation(currPrice, x, t);

            expectedMinChange = Math.min(expectedChange, expectedMinChange);
            currPrice = price + (int)Math.floor(expectedMinChange);
        }

        return expectedMinChange;
    }
    
    private float probabilityY (double x, int y, double t) {
        if (y > x(t,x) && x(t,x) > 0) return 0;
        if (y < -10 && x(t,x) > 0) return 0;
        if (y < x(t,x) && x(t,x) < 0) return 0;
        if (y > 10 && x(t,x) < 0) return 0;

        if (x(t,x) != 0) return 1.0f / (float)( 11.0 + Math.abs((int)x(t,x)) );
        return 1.0f/21.0f;
    }
    private double x (double t, double x) { return 10.0+t*(x-10.0)/54.0; }
}