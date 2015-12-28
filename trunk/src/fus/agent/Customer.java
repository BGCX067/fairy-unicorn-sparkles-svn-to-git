/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package fus.agent;

import java.util.Arrays;
import se.sics.tac.aw.Quote;
import se.sics.tac.aw.TACAgent;

/**
 *
 * @author David
 */
class Customer{

    public final int initialInFlight;
    public final int initialOutFlight;

    private final int clientNumber;
    
    private final FUSAgent impl;
    private final int dayCount;
    private final boolean[] booking;
    private final float hotelBonus;
    private final float alligatorBonus;
    private final float amusementBonus;
    private final float museumBonus;
    private final float[][] hotelPricePlan;
    private final float[][] profitability;

    private final int[][] entertainmentPricePlan;
    private int[] prefs;
    private int inFlight;
    private int outFlight;

    public Customer(FUSAgent a,
                    int in,
                    int out,
                    float hBonus,
                    float alBonus,
                    float amBonus,
                    float muBonus,                    
                    int dc,
                    int clientNumber){

        int auction;
        
        impl = a;
        
        dayCount = dc;
        
        booking = new boolean[2];
        booking[0] = true;
        booking[1] = true;

        initialInFlight = in;
        inFlight = in;
        auction = TACAgent.getAuctionFor(TACAgent.CAT_FLIGHT, TACAgent.TYPE_INFLIGHT, inFlight);
        impl.getAgent().setAllocation(auction, impl.getAgent().getAllocation(auction) + 1);
        
        initialOutFlight = out;
        outFlight = out;
        auction = TACAgent.getAuctionFor(TACAgent.CAT_FLIGHT, TACAgent.TYPE_OUTFLIGHT, outFlight);
        impl.getAgent().setAllocation(auction, impl.getAgent().getAllocation(auction) + 1);
        
        impl.getHotels().calculateClientAllocation(inFlight, outFlight);

        hotelBonus = hBonus;
        alligatorBonus = alBonus;
        amusementBonus = amBonus;
        museumBonus = muBonus;
        
        

        this.clientNumber = clientNumber;
        
        hotelPricePlan = new float[2][dayCount];
        entertainmentPricePlan = new int[3][dayCount];
        profitability = new float[3][dayCount];
        prefs = new int[3];
        
        init();
    }
    
    public void init(){          
        prefs[0] = impl.getAgent().getClientPreference(clientNumber, TACAgent.E1);
        prefs[1] = impl.getAgent().getClientPreference(clientNumber, TACAgent.E2);
        prefs[2] = impl.getAgent().getClientPreference(clientNumber, TACAgent.E3);
//        System.out.println("PREFS " +prefs[0] + " " +prefs[1]+ " " +prefs[2]);
        for(int type = 0; type < 3; type++){
            for(int day = 0; day < dayCount; day++){
                profitability[type][day] = -1;
            }
        }               
    }

    //Hotels

    public boolean wantsSingleNight(){
        return initialInFlight - initialOutFlight == 1;
    }
    
    public boolean needsSingleNight(){
        return getDuration() == 1;
    }

    public boolean bookingInHotel(int type){
        return booking[type];
    }

    public float getRoomPrice(int type,int day){
        if (booking[type]){
            return hotelPricePlan[type][day - 1];
        }
        return 0;
    }

    public void stopBookingHotel(int type){
        if (booking[type]){
            booking[type] = false;
        
            for (int d = inFlight; d < outFlight; d++){
                int auction = TACAgent.getAuctionFor(TACAgent.CAT_HOTEL, type, d);
                impl.getAgent().setAllocation(auction, impl.getAgent().getAllocation(auction) - 1);
            }
        }
    }

    public boolean setRoomPrice(int type,int day, float price){
        if (bookingInHotel(type)){
            hotelPricePlan[type][day - 1] = price;
            return true;
        }
        return false;
    }

    public float getPerNightBonus(){
        return hotelBonus/(outFlight-inFlight);
    }

    //Flights

    public int getInboundDay(){
        return inFlight;
    }

    public int getOutboundDay(){
        return outFlight;
    }
    
    public int getDuration(){
        return outFlight - inFlight;
    }
    
    public void setSingleDay(int day, int type){
        int auction;
        int otherType = type == TACAgent.TYPE_CHEAP_HOTEL ? TACAgent.TYPE_GOOD_HOTEL
                                                          : TACAgent.TYPE_CHEAP_HOTEL;
        
        //Hotels
        if (day >= inFlight && day < outFlight){
            if (booking[type]){
                for (int d = inFlight; d < day; d++){
                    auction = TACAgent.getAuctionFor(TACAgent.CAT_HOTEL, type, d);
                    impl.getAgent().setAllocation(auction, impl.getAgent().getAllocation(auction) - 1);
                }
                for (int d = day + 1; d < outFlight; d++){
                    auction = TACAgent.getAuctionFor(TACAgent.CAT_HOTEL, type, d);
                    impl.getAgent().setAllocation(auction, impl.getAgent().getAllocation(auction) - 1);
                }
            }else{
                auction = TACAgent.getAuctionFor(TACAgent.CAT_HOTEL, type, day);
                impl.getAgent().setAllocation(auction, impl.getAgent().getAllocation(auction) + 1);
                booking[type] = true;
            }
        }else{
            if (booking[type]){
                for (int d = inFlight; d < outFlight; d++){
                    auction = TACAgent.getAuctionFor(TACAgent.CAT_HOTEL, type, d);
                    impl.getAgent().setAllocation(auction, impl.getAgent().getAllocation(auction) - 1);
                }
            }else{
                booking[type] = true;
            }
            auction = TACAgent.getAuctionFor(TACAgent.CAT_HOTEL, type, day);
            impl.getAgent().setAllocation(auction, impl.getAgent().getAllocation(auction) + 1);
        }
        stopBookingHotel(otherType);
        
        //Flights
        if (day != inFlight){
            auction = TACAgent.getAuctionFor(TACAgent.CAT_FLIGHT, TACAgent.TYPE_INFLIGHT, inFlight);
            impl.getAgent().setAllocation(auction, impl.getAgent().getAllocation(auction) - 1);

            inFlight = day;

            auction = TACAgent.getAuctionFor(TACAgent.CAT_FLIGHT, TACAgent.TYPE_INFLIGHT, inFlight);
            impl.getAgent().setAllocation(auction, impl.getAgent().getAllocation(auction) + 1);
        }
        
        if (day + 1 != outFlight){
            auction = TACAgent.getAuctionFor(TACAgent.CAT_FLIGHT, TACAgent.TYPE_OUTFLIGHT, outFlight);
            impl.getAgent().setAllocation(auction, impl.getAgent().getAllocation(auction) - 1);

            outFlight = day + 1;

            auction = TACAgent.getAuctionFor(TACAgent.CAT_FLIGHT, TACAgent.TYPE_OUTFLIGHT, outFlight);
            impl.getAgent().setAllocation(auction, impl.getAgent().getAllocation(auction) + 1);
        }
    }

    public void setInboundDay(int day){
        int auction;
        System.out.println("FLIGHT IN CHANGED BEFORE: " + inFlight + " AFTER: " + day);
        //Hotels
        if (day < inFlight){
            if (bookingInHotel(TACAgent.TYPE_CHEAP_HOTEL))
                for (int d = day; d < inFlight; d++){
                    auction = TACAgent.getAuctionFor(TACAgent.CAT_HOTEL, TACAgent.TYPE_CHEAP_HOTEL, d);
                    impl.getAgent().setAllocation(auction, impl.getAgent().getAllocation(auction) + 1);
                }
            if (bookingInHotel(TACAgent.TYPE_GOOD_HOTEL)){
                for (int d = day; d < inFlight; d++){
                    auction = TACAgent.getAuctionFor(TACAgent.CAT_HOTEL, TACAgent.TYPE_GOOD_HOTEL, d);
                    impl.getAgent().setAllocation(auction, impl.getAgent().getAllocation(auction) + 1);
                }
                
                for(int d = day; d < inFlight; d++){
                    
                }
            }
        }else if (day > inFlight){
            if (bookingInHotel(TACAgent.TYPE_CHEAP_HOTEL))
                for (int d = inFlight; d < day; d++){
                    auction = TACAgent.getAuctionFor(TACAgent.CAT_HOTEL, TACAgent.TYPE_CHEAP_HOTEL, d);
                    impl.getAgent().setAllocation(auction, impl.getAgent().getAllocation(auction) - 1);
                }
            if (bookingInHotel(TACAgent.TYPE_GOOD_HOTEL))
                for (int d = inFlight; d < day; d++){
                    auction = TACAgent.getAuctionFor(TACAgent.CAT_HOTEL, TACAgent.TYPE_GOOD_HOTEL, d);
                    impl.getAgent().setAllocation(auction, impl.getAgent().getAllocation(auction) - 1);
                }
        }
        
        //Flights
        if (day != inFlight){
            auction = TACAgent.getAuctionFor(TACAgent.CAT_FLIGHT, TACAgent.TYPE_INFLIGHT, inFlight);
            impl.getAgent().setAllocation(auction, impl.getAgent().getAllocation(auction) - 1);

            inFlight = day;

            auction = TACAgent.getAuctionFor(TACAgent.CAT_FLIGHT, TACAgent.TYPE_INFLIGHT, inFlight);
            impl.getAgent().setAllocation(auction, impl.getAgent().getAllocation(auction) + 1);
        }
        
        //entertainment
//        this.resetEntAlslocations();
//        impl.calculateEntertainmentAllocations(inFlight, this.outFlight, this.clientNumber);
        updateProfitabilityMatrix();
    }

    public void setOutboundDay(int day){
        int auction;
        System.out.println("FLIGHT OUT CHANGED BEFORE: " + outFlight + " AFTER: " + day);
        //Hotels
        if (day > outFlight){
            if (bookingInHotel(TACAgent.TYPE_CHEAP_HOTEL))
                for (int d = outFlight; d < day; d++){
                    auction = TACAgent.getAuctionFor(TACAgent.CAT_HOTEL, TACAgent.TYPE_CHEAP_HOTEL, d);
                    impl.getAgent().setAllocation(auction, impl.getAgent().getAllocation(auction) + 1);
                }
            if (bookingInHotel(TACAgent.TYPE_GOOD_HOTEL))
                for (int d = outFlight; d < day; d++){
                    auction = TACAgent.getAuctionFor(TACAgent.CAT_HOTEL, TACAgent.TYPE_GOOD_HOTEL, d);
                    impl.getAgent().setAllocation(auction, impl.getAgent().getAllocation(auction) + 1);
                }
        }else if (day < outFlight){
            if (bookingInHotel(TACAgent.TYPE_CHEAP_HOTEL))
                for (int d = day; d < outFlight; d++){
                    auction = TACAgent.getAuctionFor(TACAgent.CAT_HOTEL, TACAgent.TYPE_CHEAP_HOTEL, d);
                    impl.getAgent().setAllocation(auction, impl.getAgent().getAllocation(auction) - 1);
                }
            if (bookingInHotel(TACAgent.TYPE_GOOD_HOTEL))
                for (int d = day; d < outFlight; d++){
                    auction = TACAgent.getAuctionFor(TACAgent.CAT_HOTEL, TACAgent.TYPE_GOOD_HOTEL, d);
                    impl.getAgent().setAllocation(auction, impl.getAgent().getAllocation(auction) - 1);
                }
        }
        
        //Flights
        if (day != outFlight){
            auction = TACAgent.getAuctionFor(TACAgent.CAT_FLIGHT, TACAgent.TYPE_OUTFLIGHT, outFlight);
            impl.getAgent().setAllocation(auction, impl.getAgent().getAllocation(auction) - 1);

            outFlight = day;

            auction = TACAgent.getAuctionFor(TACAgent.CAT_FLIGHT, TACAgent.TYPE_OUTFLIGHT, outFlight);
            impl.getAgent().setAllocation(auction, impl.getAgent().getAllocation(auction) + 1);
        }

        //entertainment
//        this.resetEntAllocations();
//        impl.calculateEntertainmentAllocations(inFlight, this.outFlight, this.clientNumber);
        updateProfitabilityMatrix();
    }
    
    //Entertainment
    
    //TODO implement customer accessor functions

    void setEntertainmentPricePlan(int eType, int day, int alloc) {
        this.entertainmentPricePlan[eType][day] += 1;
    }
    
    int getFavouredEnt(){
                //alligator is best
        if(alligatorBonus > amusementBonus && amusementBonus > museumBonus){
            return TACAgent.TYPE_ALLIGATOR_WRESTLING;
        }
        if(amusementBonus > alligatorBonus && alligatorBonus > museumBonus){
            return TACAgent.TYPE_AMUSEMENT;
        }
        if(museumBonus > alligatorBonus && alligatorBonus > amusementBonus){
            return TACAgent.TYPE_MUSEUM;
        }
        return -1;
        
    }
    
    void resetEntAllocations(){
        for(int type = 0; type < entertainmentPricePlan.length; type++){
//
            for(int day = 0; day < entertainmentPricePlan[type].length; day++){
                int auction = (type*4) + 15 + day;
                
                if(entertainmentPricePlan[type][day] > 0){
//                    System.out.print("Resetting " + this.clientNumber + ": ");
//                System.out.print("DAY " + day+ " ");
//                System.out.print("TYPE " + type+ " ");
//                System.out.print("AUCTION " + auction+ " ");
//                System.out.print("PLAN TICKETS " + entertainmentPricePlan[type][day] + " ");
//                System.out.print("ALLOC " + impl.getAgent().getAllocation(auction)+ " ");
                
                System.out.println("");
                    
                    entertainmentPricePlan[type][day]--;
                    impl.getAgent().setAllocation(auction, impl.getAgent().getAllocation(auction) - 1);
//                    System.out.println("RESETTING "+type + " " + day+ " ALLOC: " + entertainmentPricePlan[type][day] + " " + impl.getAgent().getAllocation(auction));
                }               
            }            
        }
    }

    public int[][] getEntertainmentPricePlan() {
        return this.entertainmentPricePlan;
    }
    
    public void updateProfitabilityMatrix(){
//        int day = TACAgent.getAuctionDay(quote.getAuction());
//        int type = TACAgent.getAuctionType(quote.getAuction());
        synchronized(profitability){
            for(int type = 0; type < 3; type++){
                for(int day = 0; day < dayCount; day++){
                    profitability[type][day] = -1;
                }
            }

            for(int type = 0; type < 3; type++){
                for(int day = 0; day < dayCount; day++){
                    if(day >= inFlight && day < outFlight){
                        int auction = impl.getAgent().getAuctionFor(TACAgent.CAT_ENTERTAINMENT, type+1, day+1);
                        float profit = prefs[type] - 80  / ((impl.getAgent().getOwn(auction)/2) + 1);
    //            System.out.println("UPDATING PROFITABILITY " + profit + " " +  prefs[type-1]);
                    if(profit > 10f)
                        profitability[type][day] = profit;
                    }
                }
            }
        }
    }

    public float getProfitability(int day, int type) {
        synchronized(profitability){
            return this.profitability[type-1][day-1];
        }
    }

    public void setProfitability(int day, int type, float f) {
//        System.out.println("SETTING: " + day + type);
        synchronized(profitability){
            this.profitability[type-1][day-1] = f;
        }
    }

}