/*
 * Copyright (C) 2007 by
 * 
 * 	Xuan-Hieu Phan
 *	hieuxuan@ecei.tohoku.ac.jp or pxhieu@gmail.com
 * 	Graduate School of Information Sciences
 * 	Tohoku University
 * 
 *  Cam-Tu Nguyen
 *  ncamtu@gmail.com
 *  College of Technology
 *  Vietnam National University, Hanoi
 *
 * JGibbsLDA is a free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published
 * by the Free Software Foundation; either version 2 of the License,
 * or (at your option) any later version.
 *
 * JGibbsLDA is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with JGibbsLDA; if not, write to the Free Software Foundation,
 * Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA.
 */

package org.librairy.client.topics;

import java.io.FileNotFoundException;

import org.kohsuke.args4j.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JGibbLabeledLDA
 */
public class LDA
{
    private static final Logger LOG = LoggerFactory.getLogger(LDA.class);

    public static void main(String args[])
    {
        LDACmdOption option = new LDACmdOption();
        CmdLineParser parser = new CmdLineParser(option);

        try {
            if (args.length == 0){
                showHelp(parser);
                return;
            }

            parser.parseArgument(args);

            if (option.est || option.estc){
                Estimator estimator = new Estimator(option);
                estimator.estimate();
            }
            else if (option.inf){
                Inferencer inferencer = new Inferencer(option);
                Model newModel = inferencer.inference();
            }
        } catch (CmdLineException cle){
            LOG.debug("Command line error: " + cle.getMessage());
            showHelp(parser);
            return;
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return;
        } catch (Exception e){
            LOG.debug("Error in main: " + e.getMessage());
            e.printStackTrace();
            return;
        }
    }

    public static void showHelp(CmdLineParser parser){
        LOG.debug("LDA [options ...] [arguments...]");
        parser.printUsage(System.out);
    }

}
