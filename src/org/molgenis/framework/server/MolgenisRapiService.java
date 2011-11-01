package org.molgenis.framework.server;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.URL;

import javax.servlet.http.HttpServletRequest;

import org.apache.log4j.Logger;

public class MolgenisRapiService implements MolgenisService
{
	Logger logger = Logger.getLogger(MolgenisRapiService.class);
	/**
	 * Delegate to handle request for the R api.
	 * 
	 * @param request
	 * @param response
	 * @throws IOException
	 */
	public void handleRequest(MolgenisRequest r,
			MolgenisResponse response) throws IOException
	{
		PrintWriter out = response.getWriter();

		HttpServletRequest request = r.getRequest();
		String filename = request.getRequestURI().substring(
				request.getServletPath().length()
						+ request.getContextPath().length());
		logger.info("getting file: " + filename);
		logger.info("url: " + request.getRequestURL());
		logger.info("port: " + request.getLocalPort());
		logger.info("port: " + request.getLocalAddr());
		if (filename.startsWith("/")) filename = filename.substring(1);

		// if R file exists, return that
		if (!filename.equals("") && !filename.endsWith(".R"))
		{
			out.println("you can only load .R files");
		}
		else if (filename.equals(""))
		{
			logger.info("getting default file");
			// String server =
			// this.getMolgenisHostName()+request.getContextPath();
			String localName = request.getLocalName();
			if (localName.equals("0.0.0.0")) localName = "localhost";
			String server = "http://" + localName + ":"
					+ request.getLocalPort() + request.getContextPath();
			String rSource = server + "/api/R/";
			// getRequestURL omits port!
			out.println("#step1: (first time only) install RCurl package from omegahat or bioconductor, i.e. <br>");
			out.println("#source(\"http://bioconductor.org/biocLite.R\")<br>");
			out.println("#biocLite(\"RCurl\")<br>");
			out.println();
			out.println("#step2: source this file to use the MOLGENIS R interface, i.e. <br>");
			out.println("#source(\"" + rSource + "\")<br>");
			out.println();
			out.println("molgenispath <- paste(\"" + rSource + "\")");
			out.println();
			out.println("serverpath <- paste(\"" + server + "\")");
			out.println();
			out.println("#load autogenerated R interfaces<br>");
			out.println("source(\"" + rSource + "source.R\")");
			out.println();
			// out.println("#load R/qtl to XGAP functions<br>");
			// out.println("source(\"" + rSource + "rqtl.R\")");
			out.println();
			// out.println(
			// "#load dbGG specific extension to ease use of the Data <- DataElement structure as matrices<br>"
			// );
			// out.println("source(\"" + rSource + this.getMolgenisVariantID() +
			// "/R/DataMatrix.R\")");
			out.println();
			out.println("#connect to the server<br>");
			out.println("MOLGENIS.connect(\"" + server + "\")");
			// chdir=T means temporarily change working directory
		}
		// otherwise return the default R code to source all
		else
		{
			// the path may contain package name, e.g.
			// package.name.path/R/myclass.R
			filename = filename.replace(".", "/");
			filename = filename.substring(0, filename.length() - 2) + ".R";
			// map to hard drive, minus path papp/servlet
			File root = new File(this.getClass().getResource("source.R")
					.getFile()).getParentFile().getParentFile().getParentFile();

			if (filename.equals("source.R"))
			{
				root = new File(root.getAbsolutePath() + "/app/servlet");
			}
			File source = new File(root.getAbsolutePath() + "/" + filename);

			// up to root of app
			logger.info("trying to load R file: " + filename + " from path "
					+ source);
			if (source.exists())
			{
				this.writeURLtoOutput(source.toURI().toURL(), out);
			}
			else
			{
				out.write("File '" + filename + "' not found");
			}
		}

		out.close();
	}

	/**
	 * Helper function to write an URL to an outputstream. E.g. used to pass
	 * files that are stored elsewhere as proxy.
	 * 
	 * @param source
	 * @param out
	 * @throws IOException
	 */
	private void writeURLtoOutput(URL source, PrintWriter out)
			throws IOException
	{
		BufferedReader reader = new BufferedReader(new InputStreamReader(
				source.openStream()));
		String sourceLine;
		while ((sourceLine = reader.readLine()) != null)
		{
			out.println(sourceLine);
		}
		reader.close();
	}
}