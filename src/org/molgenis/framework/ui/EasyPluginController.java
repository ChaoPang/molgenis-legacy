package org.molgenis.framework.ui;

import java.io.OutputStream;
import java.lang.reflect.Method;

import org.molgenis.framework.db.Database;
import org.molgenis.framework.ui.ScreenModel.Show;
import org.molgenis.util.Entity;
import org.molgenis.util.HandleRequestDelegationException;
import org.molgenis.util.Tuple;

/**
 * Simplified controller that handles a lot of the hard stuff in handleRequest.
 */
public abstract class EasyPluginController<M extends ScreenModel> extends SimpleScreenController<M>
{
	private static final long serialVersionUID = 1L;

	// hack to be able to 'correctly' handle redirects (do not continue handling
	// this request after HandleRequest in AbstMolgServlet is done - contrary to
	// usual response serving which is 'fall through' and therefore wrong) and
	// at the same time allow EasyPlugins to throw exceptions which are all
	// thrown as InvocationTargetException due to reflection, while being able
	// to render the resulting page + the exception on screen
	public static Boolean HTML_WAS_ALREADY_SERVED;

	public EasyPluginController(String name, M model, ScreenController<?> parent)
	{
		super(name, model, parent);
	}

	/**
	 * If a user sends a request it can be handled here. Default, it will be
	 * automatically mapped to methods based request.getAction();
	 * 
	 * @throws HandleRequestDelegationException
	 */
	@Override
	public void handleRequest(Database db, Tuple request) throws HandleRequestDelegationException
	{
		// automatically calls functions with same name as action
		delegate(request.getAction(), db, request, null);
	}

	@Override
	public Show handleRequest(Database db, Tuple request, OutputStream out) throws HandleRequestDelegationException
	{
		// automatically calls functions with same name as action
		delegate(request.getAction(), db, request, out);

		// default show
		return Show.SHOW_MAIN;
	}
	
	@Deprecated
	public void delegate(String action, Database db, Tuple request) throws HandleRequestDelegationException
	{
		this.delegate(action,db,request,null);
	}

	public void delegate(String action, Database db, Tuple request, OutputStream out)
			throws HandleRequestDelegationException
	{
		// try/catch for db.rollbackTx
		try
		{
			// try/catch for method calling
			try
			{
				db.beginTx();
				logger.debug("trying to use reflection to call " + this.getClass().getName() + "." + action);
				Method m = this.getClass().getMethod(action, Database.class, Tuple.class);
				m.invoke(this, db, request);
				logger.debug("call of " + this.getClass().getName() + "(name=" + this.getName() + ")." + action
						+ " completed");
				if (db.inTx()) db.commitTx();
			}
			catch (NoSuchMethodException e1)
			{
				this.getModel().setMessages(new ScreenMessage("Unknown action: " + action, false));
				logger.error("call of " + this.getClass().getName() + "(name=" + this.getName() + ")." + action
						+ "(db,tuple) failed: " + e1.getMessage());
				db.rollbackTx();
				// useless - can't do this on every error! we cannot distinguish
				// exceptions because they are all InvocationTargetException
				// anyway
				// }catch (InvocationTargetException e){
				// throw new RedirectedException(e);

				if (out != null) try
				{
					db.beginTx();
					logger.debug("trying to use reflection to call " + this.getClass().getName() + "." + action);
					Method m = this.getClass().getMethod(action, Database.class, Tuple.class, OutputStream.class);
					m.invoke(this, db, request, out);
					logger.debug("call of " + this.getClass().getName() + "(name=" + this.getName() + ")." + action
							+ " completed");
					if (db.inTx()) db.commitTx();
				}
				catch (Exception e)
				{
					this.getModel().setMessages(new ScreenMessage("Unknown action: " + action, false));
					logger.error("call of " + this.getClass().getName() + "(name=" + this.getName() + ")." + action
							+ "(db,tuple) failed: " + e1.getMessage());
					db.rollbackTx();
				}
			}
			catch (Exception e)
			{
				logger.error("call of " + this.getClass().getName() + "(name=" + this.getName() + ")." + action
						+ " failed: " + e.getMessage());
				e.printStackTrace();
				this.getModel().setMessages(new ScreenMessage(e.getCause().getMessage(), false));
				db.rollbackTx();
			}
		}
		// catch (RedirectedException e){
		// throw e;
		// }
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}

	public void setError(String message)
	{
		this.getModel().setMessages(new ScreenMessage(message, false));
	}

	public void setSucces(String message)
	{
		this.getModel().setMessages(new ScreenMessage(message, true));
	}

	public <E extends Entity> FormModel<E> getParentForm(Class<E> entityClass)
	{
		// here we gonna put the parent
		ScreenController parent = getParent();
		while (parent != null)
		{
			if (parent instanceof FormController && ((FormController<?>) parent).getEntityClass().equals(entityClass))
			{
				return (FormModel<E>) parent.getModel();
			}
			else
			{
				parent = (ScreenController) parent.getParent();
			}
		}
		throw new RuntimeException("Parent form of class " + entityClass.getName() + " is unknown in plugin name="
				+ getName());
	}

}
