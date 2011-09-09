package com.linkedin.clustermanager.participant;

import java.util.List;

import org.I0Itec.zkclient.exception.ZkNodeExistsException;
import org.apache.log4j.Logger;
import org.apache.zookeeper.CreateMode;

import com.linkedin.clustermanager.ClusterDataAccessor;
import com.linkedin.clustermanager.ClusterDataAccessor.ControllerPropertyType;
import com.linkedin.clustermanager.ClusterManager;
import com.linkedin.clustermanager.ControllerChangeListener;
import com.linkedin.clustermanager.NotificationContext;
import com.linkedin.clustermanager.ZNRecord;
import com.linkedin.clustermanager.controller.ClusterManagerMain;
import com.linkedin.clustermanager.controller.GenericClusterController;


public class DistClusterControllerElection implements ControllerChangeListener
{
	private static Logger logger = Logger
	    .getLogger(DistClusterControllerElection.class);
	private GenericClusterController _controller = null;

	public GenericClusterController getController()
	{
	  return _controller;
	}
	
	private void doLeaderElection(ClusterManager manager) throws Exception
	{
		boolean isLeader = tryUpdateController(manager);
		if (isLeader)
		{
			_controller = new GenericClusterController();
			
			/**
			manager.addConfigChangeListener(_controller);
			manager.addLiveInstanceChangeListener(_controller);
			manager.addIdealStateChangeListener(_controller);
			manager.addExternalViewChangeListener(_controller);
			**/
			
			ClusterManagerMain.addListenersToController(manager, _controller);
		}
	}

	@Override
	public void onControllerChange(NotificationContext changeContext)
	{
		ClusterManager manager = changeContext.getManager();
		try
		{
			if (changeContext.getType().equals(NotificationContext.Type.INIT)
			    || changeContext.getType().equals(NotificationContext.Type.CALLBACK))
			{

				doLeaderElection(manager);
			}
			if (changeContext.getType().equals(NotificationContext.Type.FINALIZE))
			{
				if (_controller != null)
				{
					// do clean
					manager.removeListener(_controller);
				}
			
			}
		} catch (Exception e)
		{
			logger.error("Exception when trying to become leader" + e);
		}
	}

	private boolean tryUpdateController(ClusterManager manager)
	{
		try
		{
			String instanceName = manager.getInstanceName();
			String clusterName = manager.getClusterName();
			final ZNRecord leaderRecord = new ZNRecord();
			leaderRecord.setId(ControllerPropertyType.LEADER.toString());
			leaderRecord.setSimpleField("Leader", manager.getInstanceName());
			ClusterDataAccessor dataAccessor = manager.getDataAccessor();
			ZNRecord currentleader = dataAccessor
			    .getControllerProperty(ControllerPropertyType.LEADER);
			if (currentleader == null)
			{
				dataAccessor.createControllerProperty(ControllerPropertyType.LEADER,
				    leaderRecord, CreateMode.EPHEMERAL);
				// set controller history
				ZNRecord histRecord = dataAccessor
				    .getControllerProperty(ControllerPropertyType.HISTORY);

				List<String> list = histRecord.getListField(clusterName);

				list.add(instanceName);
				dataAccessor.setControllerProperty(ControllerPropertyType.HISTORY,
				    histRecord, CreateMode.PERSISTENT);
				return true;
			} else
			{
				logger.info("Leader exists for cluster:" + clusterName
				    + " currentLeader:" + currentleader.getId());
			}

		} catch (ZkNodeExistsException e)
		{
			logger.warn("Ignorable exception. Found that leader already exists"
			    + e.getMessage());
		}
		return false;
	}

}