package org.molgenis.framework.server.services;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.apache.log4j.Logger;
import org.molgenis.framework.db.Database;
import org.molgenis.framework.db.DatabaseException;
import org.molgenis.framework.db.QueryRule;
import org.molgenis.framework.security.Login;
import org.molgenis.framework.server.MolgenisContext;
import org.molgenis.framework.server.MolgenisRequest;
import org.molgenis.framework.server.MolgenisResponse;
import org.molgenis.framework.server.MolgenisService;
import org.molgenis.framework.ui.ApplicationController;
import org.molgenis.framework.ui.EasyPluginController;
import org.molgenis.framework.ui.FormModel;
import org.molgenis.framework.ui.ScreenController;
import org.molgenis.framework.ui.ScreenModel;
import org.molgenis.framework.ui.ScreenModel.Show;
import org.molgenis.framework.ui.html.FileInput;
import org.molgenis.util.CsvWriter;
import org.molgenis.util.Entity;
import org.molgenis.util.HttpServletRequestTuple;
import org.molgenis.util.Tuple;
import org.molgenis.util.TupleWriter;


public abstract class MolgenisGuiService
{
	Logger logger = Logger.getLogger(MolgenisGuiService.class);
	
	protected MolgenisContext mc;
	
	public MolgenisGuiService(MolgenisContext mc)
	{
		this.mc = mc;
	}
	
	public abstract ApplicationController createUserInterface(Login userLogin);
	
	public abstract Login createLogin(Database db, HttpServletRequest request) throws Exception;
	
	/**
	 * Handle use of molgenis GUI
	 * 
	 * TODO: this method is horrible and should be properly refactored,
	 * documented and tested!
	 * 
	 * @param request
	 * @param response
	 */
	public void handleRequest(MolgenisRequest request, MolgenisResponse response) throws ParseException,
			DatabaseException, IOException
	{
		// get database session (note: this shouldn't be in the tomcat
				// session!!!
				Database db = null;
				boolean dbAvailable = false;
				String dbErrorMessage = null;
				try
				{
					db = mc.getDatabase();
					// db.beginTx(); DISCUSSION
					System.err.println("???" + db);
					if (db != null)
					{
						dbAvailable = true;
						//logger.info("created database " + db);
					}
					else
					{
						dbErrorMessage = "database is NULL";
					}
				}
				catch (Exception e)
				{
					//logger.error("Database creation failed: " + e.getMessage());
					e.printStackTrace();
					// throw new DatabaseException(e);
					dbErrorMessage = e.getMessage();
				}

				// login/logout
				HttpSession session = request.getRequest().getSession();
				Login userLogin = null;
				// Get application from session (or create one)
				ApplicationController molgenis = (ApplicationController) session
						.getAttribute("application");

				// on logout throw whole session away.
				if (request.getRequest().getParameter("__action") != null
						&& request.getRequest().getParameter("__action").equalsIgnoreCase("Logout"))
				{
					molgenis = null;
					session.setAttribute("application", null);
				}
				if (molgenis == null)
				{
					try
					{
						userLogin = createLogin(db, request.getRequest());
					}
					catch (Exception e)
					{
						throw new DatabaseException(e);
					}
					if ((!userLogin.isAuthenticated() && userLogin.isLoginRequired())
							|| (request.getRequest().getParameter("logout") != null && !session
									.isNew()))
					{
						response.getResponse().setHeader("WWW-Authenticate",
								"BASIC realm=\"MOLGENIS\"");
						response.getResponse().sendError(HttpServletResponse.SC_UNAUTHORIZED);
						session.invalidate();
						return;
					}
					molgenis = createUserInterface(userLogin);
				}
				// this should work unless complicated load balancing without proxy
				// rewriting...
				molgenis.setBaseUrl(request.getRequest().getScheme() + "://"
						+ request.getRequest().getServerName() + getPort(request.getRequest())
						+ request.getRequest().getContextPath());

				// ((UserInterface)molgenis).setDatabase(db);
				userLogin = ((ApplicationController) molgenis).getLogin();

//				if (dbAvailable)
//				{
//					db.setLogin(userLogin);
//				}

				// handle request
				try
				{
					// set the base address

					Tuple requestTuple = new HttpServletRequestTuple(request.getRequest(), response.getResponse());

					// action == download an attached file
					// FIXME move to form controllers handlerequest...
					if (FileInput.ACTION_DOWNLOAD.equals(requestTuple
							.getString(ScreenModel.INPUT_ACTION)))
					{
						//logger.info(requestTuple);

						File file = new File(
								db.getFilesource()
										+ "/"
										+ requestTuple
												.getString(FileInput.INPUT_CURRENT_DOWNLOAD));

						FileInputStream filestream = new FileInputStream(file);

						response.getResponse().setContentType("application/x-download");
						response.getResponse().setContentLength((int) file.length());
						response.getResponse().setHeader(
								"Content-Disposition",
								"attachment; filename="
										+ requestTuple
												.getString(FileInput.INPUT_CURRENT_DOWNLOAD));

						BufferedOutputStream out = new BufferedOutputStream(
								response.getResponse().getOutputStream());
						byte[] buffer = new byte[1024];
						int bytes_read;
						while ((bytes_read = filestream.read(buffer)) != -1)
						{
							out.write(buffer, 0, bytes_read);
						}
						filestream.close();
						out.flush();
						out.close();
					}

					// action == download, but now in a standard way, handled by
					// controller
					else if (ScreenModel.Show.SHOW_DOWNLOAD.equals(requestTuple
							.getString(FormModel.INPUT_SHOW)))
					{
						// get the screen that will hande the download request
						ScreenController<? extends ScreenModel> controller = molgenis
								.get(requestTuple.getString(ScreenModel.INPUT_TARGET));

						// set the headers for the download
						response.getResponse().setContentType("application/x-download");

						String action = requestTuple
								.getString(ScreenModel.INPUT_ACTION);
						String extension = null;
						if (action.startsWith("download_txt_"))
						{
							extension = "txt";
						}
						else if (action.startsWith("download_xls_"))
						{
							extension = "xls";
						}
						else
						{
							throw new Exception("Download type '" + action
									+ "' unsupported!");
						}

						ScreenModel.Show.values();
						response.getResponse().setHeader("Content-Disposition",
								"attachment; filename="
										+ controller.getName().toLowerCase() + "."
										+ extension);

						// let the handleRequest produce the content
						controller.handleRequest(db, requestTuple,
								response.getResponse().getOutputStream());

						// TODO: does this fail when stream is already closed??
						response.getResponse().getOutputStream().flush();
						response.getResponse().getOutputStream().close();
					}

					// handle normal event and then write the response
					else
					{
						// capture select
						if (requestTuple.getString("select") != null)
						{
							// get the screen to be selected
							ScreenController<?> toBeSelected = molgenis
									.get(requestTuple.getString("select"));
							// select leaf in its parent
							try
							{
								toBeSelected.getParent().setSelected(
										requestTuple.getString("select"));
							}
							catch (NullPointerException npe)
							{
								// screen does not exists, ignore request
							}
						}

						if (Show.SHOW_CLOSE.equals(molgenis.handleRequest(db,
								requestTuple, null)))
						{
							//if close, then write a close script
							PrintWriter writer = response.getResponse().getWriter();
							writer.write("<html><head></head><body><script>window.close();</script></body></html>");
							writer.close();
							return;
						}

						// workaround - see comment @
						// EasyPluginController.HTML_WAS_ALREADY_SERVED
						if (EasyPluginController.HTML_WAS_ALREADY_SERVED != null
								&& EasyPluginController.HTML_WAS_ALREADY_SERVED)
						{
							EasyPluginController.HTML_WAS_ALREADY_SERVED = null;
							return;
						}

						// handle request
						molgenis.reload(db); // reload the application
	

						// session are automatically synchronized...
						session.setAttribute("application", molgenis);

						// prepare the response
						response.getResponse().setContentType("text/html");
						// response.setBufferSize(10000);
						PrintWriter writer = response.getResponse().getWriter();

						

						// Render result
						String show = requestTuple.getString(FormModel.INPUT_SHOW);
						if (ScreenModel.Show.SHOW_DIALOG.equals(show))
						{
							molgenis.getModel().setShow(show);
							ScreenController<?> target = molgenis.get(requestTuple
									.getString("__target"));
							molgenis.getModel().setTarget(target);
							writer.write(molgenis.render());
						}
						else if ("massupdate".equals(show))
						{
							molgenis.getModel().setShow("show");
							writer.write(molgenis.render());

						}
						else
						{
							molgenis.getModel().setShow("root");
							writer.write(molgenis.render());
						}

						
						writer.close();

						// done, get rid of screen messages here?
						((ApplicationController) molgenis).clearAllMessages();

					}
					// db.commitTx(); DISCUSSION
				}
				catch (Exception e)
				{
					// response.getOutputStream().print(e.getMessage());
					//logger.error(e.getMessage());
					e.printStackTrace();
					// unrecoverable error? does this take down the whole server?
					// throw new RuntimeException(e);
				}
		
	}	
	
	private static String getPort(HttpServletRequest req)
	{
		if ("http".equalsIgnoreCase(req.getScheme())
				&& req.getServerPort() != 80
				|| "https".equalsIgnoreCase(req.getScheme())
				&& req.getServerPort() != 443)
		{
			return (":" + req.getServerPort());
		}
		else
		{
			return "";
		}
	}

}
