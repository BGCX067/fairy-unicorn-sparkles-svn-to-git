/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package fus.agent;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.GridLayout;
import java.io.*;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import se.sics.tac.aw.*;

public class Entertainment {
    protected TACAgent agent;
    protected FUSAgent impl;

    private float[] lastBid;
    private float[] lastAsk;
    
    private float[] prices;
    private float[][] askHistory;
    private float[][] bidHistory;
    private float[] askCount;
    private float[] bidCount;
    private float[][] askAverage;
    private float[][] bidAverage;
    private int[][] askGameCount;
    private int[][] bidGameCount;
    private long[] lastQuote;
    
    private int bidCounter = 0;

    
//    private ArrayList<float[][]> askHistory;


    public Entertainment(FUSAgent impl) {
        this.agent = impl.getAgent();
        this.impl = impl;
        
        lastBid = new float[agent.getAuctionNo()];
        lastAsk = new float[agent.getAuctionNo()];

        prices = new float[agent.getAuctionNo()];
//        askHistory = new float[agent.getAuctionNo()];
        bidHistory = new float[agent.getAuctionNo()][18];
        askCount = new float[agent.getAuctionNo()];
        bidCount = new float[agent.getAuctionNo()];
        lastQuote = new long[agent.getAuctionNo()];
//        askAverage = new float[agent.getAuctionNo()];
        askAverage = new float[agent.getAuctionNo()][18];
        bidAverage = new float[agent.getAuctionNo()][18];
        askHistory = new float[agent.getAuctionNo()][18];        
        
        
        askGameCount = new int[12][18];
        bidGameCount = new int[12][18];

        
//        frame = new JFrame();
//        drawPrices = new DrawPrices[12];
//        
//        frame.setLayout(new GridLayout(13,1));
//
//	frame.add(new JLabel("ask price history and average"));
//        
//
//        for(int auction=0; auction<12; auction++) {
//            drawPrices[auction] = new DrawPrices(agent.getAllocation(auction + 16));            
//            frame.add(drawPrices[auction]);
//        }
//
//        frame.setSize(400, 900);
//        frame.setVisible(true);
        this.init();
        
    }
    
    public void init(){
        //read from files and make average
        updateKnowledge();
    }

    public void updateKnowledge(){
                try{
      // Open the file that is the first 
      // command line parameter
      FileInputStream fstream = new FileInputStream("askHistory.txt");
      ArrayList<float[][]> askHistoryGames = new ArrayList<float[][]>();
//      float[][] askHistory = new float[12][18];
//      float[][] askAverage = new float[12][18];
//      int[][] gameCount = new int[12][18];
      
      for(int i=0; i < 12; i++){
          for(int j=0; j < 18; j++){
            askGameCount[i][j] = 1;
          }          
      }
      // Get the object of DataInputStream
      DataInputStream in = new DataInputStream(fstream);
      BufferedReader br = new BufferedReader(new InputStreamReader(in));
      String strLine;
      //Read File Line By Line
      int lineCount = 0;
//      int gameCount = 1;
      while ((strLine = br.readLine()) != null)   {
        // Print the content on the console          
        String[] commaSplit = strLine.split(","); 
        for(int i=0; i < commaSplit.length; i++){
            askHistory[lineCount][i] = Float.parseFloat(commaSplit[i]);
            float currentAsk = askHistory[lineCount][i];
            if(currentAsk != 0){
                askGameCount[lineCount][i]++;
                float askCurrAverage = askAverage[lineCount][i];
                float askNewAverage;
                if(askCurrAverage == 0)
                    askNewAverage = currentAsk;
                else
                    askNewAverage = askCurrAverage + ((currentAsk - askCurrAverage)/(float)askGameCount[lineCount][i]);                       
                askAverage[lineCount][i] = askNewAverage;
            }
            
        }
        lineCount++;
        
        if(lineCount % 12 == 0){
            askHistoryGames.add(askHistory);
            lineCount = 0;
        }        
      }
      
      //read in the bid history
      fstream = new FileInputStream("bidHistory.txt");

      in = new DataInputStream(fstream);
      br = new BufferedReader(new InputStreamReader(in));
      
      //Read File Line By Line
      lineCount = 0;
//      int gameCount = 1;
      while ((strLine = br.readLine()) != null)   {
        // Print the content on the console          
        String[] commaSplit = strLine.split(","); 
        for(int i=0; i < commaSplit.length; i++){
            bidHistory[lineCount][i] = Float.parseFloat(commaSplit[i]);
            float currentAsk = bidHistory[lineCount][i];
            if(currentAsk != 0){
                bidGameCount[lineCount][i]++;
                float askCurrAverage = bidAverage[lineCount][i];
                float askNewAverage;
                if(askCurrAverage == 0)
                    askNewAverage = currentAsk;
                else
                    askNewAverage = askCurrAverage + ((currentAsk - askCurrAverage)/(float)bidGameCount[lineCount][i]);                       
                bidAverage[lineCount][i] = askNewAverage;
            }
            
        }
        lineCount++;
        
        if(lineCount % 12 == 0){
            askHistoryGames.add(bidHistory);
            lineCount = 0;
        }        
      }
      
      
//      for(int i=0; i < 12; i++){
//          for(int j=0; j < 18; j++){
//              System.out.print(bidAverage[i][j]+",");
//          }
//          System.out.print("\n");
//      }
      //Close the input stream
      in.close();
        }catch (Exception e){//Catch exception if any
      System.err.println("Error: " + e.getMessage());
      }
    }
    
    
       protected JFrame frame;
    protected DrawPrices[] drawPrices;

    public float[][] getAskAverage() {
        return this.askAverage;
    }

    
    public float[][] getBidHistory() {
        return this.bidHistory;
    }
    public float[][] getAskHistory() {
        return this.askHistory;
    }

    private void changeAuctionPrices() {
        for(int auction = 16; auction < 28; auction++){
            Quote quote = impl.getAgent().getQuote(auction);
            int alloc = agent.getAllocation(auction) - agent.getOwn(auction);
            int currentStep = (int)(agent.getGameTime()/30000);

            if(alloc !=0 ){                        
                float price;
                float desiredSell = 85f;
                float desiredBuy = 90f;

                int auctionNo = auction-16;                    
                
                if (alloc < 0){                              
                    float bias = Math.max((askAverage[auctionNo][currentStep] - bidAverage[auctionNo][currentStep]), quote.getAskPrice() - quote.getBidPrice());
//                    float newPrice = bidAverage[auctionNo][currentStep] + (bias * (bidAverage[auctionNo][currentStep] / askAverage[auctionNo][currentStep]));
                    float newPrice = askAverage[auctionNo][currentStep] - (bias * (quote.getBidPrice() / (quote.getAskPrice()+1)));                    
                    if(newPrice * 0.90 < quote.getBidPrice())
                        newPrice = newPrice * (float)0.90;                    
                    if(newPrice < desiredSell)
                        newPrice = desiredSell;

//                    System.out.println("SELLING " + newPrice + " BIAS " + bias + " ASK AVERAGE " + askAverage[auctionNo][currentStep] + " BID AVERAGE " + bidAverage[auctionNo][currentStep]);
                    price = newPrice;
                    impl.setPrice(auction, newPrice);

                    Bid bid = new Bid(auction);
                    bid.addBidPoint(alloc, newPrice);
                    impl.getAgent().submitBid(bid);
                    
                }                
                
            // buying
                else if(alloc > 0){                   
                    float bias = Math.min(askAverage[auctionNo][currentStep] - bidAverage[auctionNo][currentStep], quote.getAskPrice() - quote.getBidPrice());
                    float newPrice = bidAverage[auctionNo][currentStep] + (bias * (bidAverage[auctionNo][currentStep] / (askAverage[auctionNo][currentStep]+1)));


                    if(newPrice > desiredBuy || askAverage[auctionNo][currentStep] == 0)
                        newPrice = desiredBuy;
                    if(newPrice < quote.getAskPrice() && newPrice *1.1f > quote.getAskPrice()){
                        newPrice = quote.getAskPrice();
                    } 
                    price = newPrice;
                    Bid bid = new Bid(auction);
                    bid.addBidPoint(alloc, newPrice);
                    impl.getAgent().submitBid(bid);
                }
                    
                  
            } else{
                   Bid bid = new Bid(auction);
                   bid.addBidPoint(0, 0);
                   impl.getAgent().submitBid(bid);
            }
        } 
   }

 
    static class DrawPrices extends JPanel {
        protected float[] priceHistory;
        protected float[] averageHistory;


        protected int allocation;

        public DrawPrices(int alloc) {
            System.out.println("ALLOC " + alloc);
            priceHistory = new float[18];
            averageHistory = new float[18];
            for(int t=0; t<18; t++){
                priceHistory[t]=0;
                averageHistory[t]=0;
            }            
            this.allocation = alloc;
        }

        public void setAllocation(int alloc) {
            this.allocation = alloc;
            repaint();
        }

        public void setPrice(float p, float average, int t) {
            priceHistory[t] = p;
            averageHistory[t] = average;
            repaint();
        }

        public void clear() {
            for(int t=0; t<18; t++){
                averageHistory[t]=0;
                priceHistory[t]=0;
            }
                
            allocation = 0;

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
            float tInterval = this.getWidth() / 18.f;
            float pInterval = this.getHeight() / (800.f - 150.f);

            for(int t=0; t<17; t++) {
                if(priceHistory[t+1] == 0)
                    continue;
                g.setColor(Color.RED);
//                System.out.println("Drawing line from: (" + (int)(t*tInterval) +", " + (this.getHeight() - (int)((priceHistory[t]-150.f)*pInterval)) +") to (" + (int)((t+1)*tInterval) +", " + (this.getHeight() - (int)((priceHistory[t+1]-150.f)*pInterval)) +")");
                g.drawLine((int)(t*tInterval), this.getHeight() - (int)((priceHistory[t]-150.f)*pInterval), (int)((t+1)*tInterval), this.getHeight() - (int)((priceHistory[t+1]-150.f)*pInterval));   
            }
            g.setColor(Color.GREEN);
        }
    }
    
    

    public float initialBid(int auction) {
//        int alloc = agent.getAllocation(auction) - agent.getOwn(auction);
//        drawPrices[auction-16].clear();
//        drawPrices[auction-16].setAllocation(agent.getAllocation(auction));
//        if (alloc < 0) {
//            prices[auction] = 200f;
//            return 200;
//	} else if (alloc > 0) {
//            prices[auction] = 50f;
//            return 50;
//	}
        return 0;
    }
    
    public void updatePricePlan(){
        //list of customers for each auction
        List[] ownsAndCustomers = new ArrayList[12];
        //reset allocations for entertainment to zero
        for(int i = 16; i < 28; i ++){
            impl.getAgent().setAllocation(i, 0);
        }
        
        //loop through auctions, looking if we own any tickets
        //if we do allocate the tickets to highest profitable client
        //remove clients profitabilty so they dont affect next step
        for(int auction = 0; auction < 12; auction++){
            final int auctionFinal = auction+16;
            int owns = impl.getAgent().getOwn(auctionFinal);
            final int day = TACAgent.getAuctionDay(auctionFinal);
            final int type = TACAgent.getAuctionType(auctionFinal);

            //sort by profitiabilty
            ownsAndCustomers[auction] = impl.getCustomers();
            Collections.sort(ownsAndCustomers[auction], new Comparator<Customer>(){                                                
                @Override
                public int compare(Customer c1, Customer c2) {
                    return (int)(c2.getProfitability(day, type) - c1.getProfitability(day, type));
                }
            });
            
            for(int i = 0; i < owns; i++){                
                Customer cust = (Customer) ownsAndCustomers[auction].get(i);
                if(cust.getProfitability(day, type) > 0) {
                    for(int eraseDay = 1; eraseDay < 5; eraseDay++)
                        cust.setProfitability(eraseDay, type, -1f);
                    for(int eraseType = 1; eraseType < 4; eraseType++)
                        cust.setProfitability(day, eraseType, -1f);
                    impl.getAgent().setAllocation(auctionFinal, impl.getAgent().getAllocation(auctionFinal) + 1);
                }
            }
        }
        
        //for each customer calculate a matrix of days thatd be most profitable for each type
        for(int clientNum = 0; clientNum < 8; clientNum ++){            
            final Customer customer = impl.getCustomer(clientNum);
            //matrix of profitable days per type
            int[][] dayMatrix = new int[3][5];
            for(int type = 0; type < 3; type ++){
                ArrayList<Integer> days = new ArrayList<Integer>();
                
                final int typeFinal = type;
                //add day to list if its profitable
                for(int day = 1; day < 5; day++){
                    if(customer.getProfitability(day, typeFinal+1) > 0){
//                        System.out.println("DAY HERE:" + day);
                        days.add(day);
                    }                       
                }
                
                Collections.sort(days, new Comparator<Integer>(){
                    @Override
                    public int compare(Integer day1, Integer day2) {
                        return (int) (customer.getProfitability(day2, typeFinal+1) - customer.getProfitability(day1, typeFinal+1));
                    }
                });
                //add sorted days to matrix
                for(int i=0; i < days.size(); i++){
//                    System.out.println("DAY " + days.get(i));
                    dayMatrix[typeFinal][i] = days.get(i);
                }
            }
            
            //check for collisions
            //if collision, leave most profitable where it is, and shift the others sorted days up
            //keep checking till no collisions, most profitable days are on top row
            while(true){
                if(dayMatrix[0][0] == dayMatrix[1][0] && dayMatrix[0][0] != 0){
//                    System.out.println("DAY:" + dayMatrix[0][0]);
                    if(customer.getProfitability(dayMatrix[0][0], 1) > customer.getProfitability(dayMatrix[1][0], 2)){
                        for(int day = 1; day < 5; day++){
                            dayMatrix[1][day-1] = dayMatrix[1][day];
                        }
                    } else{
                        for(int day = 1; day < 5; day++){
                            dayMatrix[0][day-1] = dayMatrix[0][day];
                        }
                    }
                } else if(dayMatrix[0][0] == dayMatrix[2][0] && dayMatrix[0][0] != 0){
                    if(customer.getProfitability(dayMatrix[0][0], 1) > customer.getProfitability(dayMatrix[2][0], 3)){
                        for(int day = 1; day < 5; day++){
                            dayMatrix[2][day-1] = dayMatrix[2][day];
                        }
                    } else{
                        for(int day = 1; day < 5; day++){
                            dayMatrix[0][day-1] = dayMatrix[0][day];
                        }
                    }
                    
                } else if(dayMatrix[1][0] == dayMatrix[2][0] && dayMatrix[1][0] != 0){
                    if(customer.getProfitability(dayMatrix[1][0], 2) > customer.getProfitability(dayMatrix[2][0], 3)){
                        for(int day = 1; day < 5; day++){
                            dayMatrix[2][day-1] = dayMatrix[2][day];
                        }
                    } else{
                        for(int day = 1; day < 5; day++){
                            dayMatrix[1][day-1] = dayMatrix[1][day];
                        }
                    }
                    
                } else
                    break;
            }
            //loop through top row and allocate tickets for those days
            for(int i = 0; i < 3; i ++){
                if(dayMatrix[i][0] != 0){
                    int auction = TACAgent.getAuctionFor(TACAgent.CAT_ENTERTAINMENT, i+1, dayMatrix[i][0]);
                    impl.getAgent().setAllocation(auction, impl.getAgent().getAllocation(auction) + 1);
                }
            }
        }
    }
    
   private void getPrice() {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    
    public void calcNewBidPrice(Quote quote) {
        //get the auction we're bidding in
        int auction = quote.getAuction();
        
        int currentStep = (int)(agent.getGameTime()/30000);
        askHistory[auction][currentStep] = quote.getAskPrice();
        bidHistory[auction][currentStep] = quote.getBidPrice();        
        
        bidCounter++;
        
        if(bidCounter == 12){
            for(int clientNum = 0; clientNum < 8; clientNum ++){
                impl.getCustomer(clientNum).updateProfitabilityMatrix();
            }
            //do some magic
            updatePricePlan();            
            bidCounter = 0;
            changeAuctionPrices();
        }
        //selling
//        int alloc = agent.getAllocation(auction) - agent.getOwn(auction);
//        if(alloc !=0 ){
//            float diff = quote.getAskPrice() - quote.getBidPrice();
//            float price;
//            float desiredSell = 90f;
//            float desiredBuy = 90f;
//
//            int auctionNo = auction-16;
//
//            if (alloc < 0){
//
//                if(quote.getBidPrice() == 0){
//                    price = 0f;
//                } else{
//
//                    float bias = Math.max((askAverage[auctionNo][currentStep] - bidAverage[auctionNo][currentStep]), quote.getAskPrice() - quote.getBidPrice());
////                    float newPrice = bidAverage[auctionNo][currentStep] + (bias * (bidAverage[auctionNo][currentStep] / askAverage[auctionNo][currentStep]));
//                    float newPrice = askAverage[auctionNo][currentStep] - (bias * (quote.getBidPrice() / quote.getAskPrice()));
//                    if(newPrice * 0.90 < quote.getBidPrice())
//                        newPrice = newPrice * (float)0.95;
//                    if(newPrice < desiredSell)
//                        newPrice = desiredSell;
////                    System.out.println("SELLING " + newPrice + " BIAS " + bias + " ASK AVERAGE " + askAverage[auctionNo][currentStep] + " BID AVERAGE " + bidAverage[auctionNo][currentStep]);
//                    price = newPrice;
//                    impl.setPrice(auction, price);
//                    prices[auction] = price;
//                }
//
//            }
//
//            // buying
//            else if(alloc > 0){
//
//                for(int customerNumber = 0; customerNumber < 8; customerNumber++){
//                    int[][] entertainmentPricePlan = impl.getCustomer(customerNumber).getEntertainmentPricePlan();
//                    int sum = 0;
//                    for(int type = 0; type < entertainmentPricePlan.length; type++){
//                        for(int day = 0; day < entertainmentPricePlan[type].length; day++){
//                            int auc = (type*4) + 15 + day;
//
//                            if(entertainmentPricePlan[type][day] > 0){
//                               sum++;
////                                System.out.print("Resetting " + this.clientNumber + ": ");
////                                System.out.print("DAY " + day+ " ");
////                                System.out.print("TYPE " + type+ " ");
////                                System.out.print("AUCTION " + auction+ " ");
////                                System.out.print("PLAN TICKETS " + entertainmentPricePlan[type][day] + " ");
////                                System.out.print("ALLOC " + impl.getAgent().getAllocation(auction)+ " ");
////                                System.out.println("");
////                                entertainmentPricePlan[type][day]--;
////                                impl.getAgent().setAllocation(auction, impl.getAgent().getAllocation(auction) - 1);
////                                System.out.println("RESETTING "+type + " " + day+ " ALLOC: " + entertainmentPricePlan[type][day] + " " + impl.getAgent().getAllocation(auction));
//                             }
//                        }
//                    }
//                    if(sum==alloc)
//                        System.out.println("YAY");
//                    assert sum == alloc;
//                }
//
//                if(quote.getAskPrice() ==0){
//                    price = 200f;
//                } else{
//
//                    float bias = Math.min(askAverage[auctionNo][currentStep] - bidAverage[auctionNo][currentStep], quote.getAskPrice() - quote.getBidPrice());
//                    float min = askAverage[auctionNo][0];
//
//                    float newPrice = bidAverage[auctionNo][currentStep] + (bias * (bidAverage[auctionNo][currentStep] / askAverage[auctionNo][currentStep]));
//                    if(newPrice < quote.getAskPrice() && newPrice *1.1f > quote.getAskPrice()){
//                        newPrice = quote.getAskPrice();
//                    }
//
//                    if(newPrice > desiredBuy || askAverage[auctionNo][currentStep] == 0)
//                        newPrice = desiredBuy;
//                    price = newPrice;
//                    prices[auction] = price;
//                    impl.setPrice(auction, price);
//
//                }
//
//              }
//                return prices[auction];
//            }
//
//        //we don't need any things
//        else{
//             if(quote.getAskPrice() == 0){
//
//                }
//        }
//        return 0;
    }
}
