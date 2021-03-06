package webim.spring3.controller;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.ModelAndView;

import webim.WebimClient;
import webim.WebimConfig;
import webim.WebimEndpoint;
import webim.WebimGroup;
import webim.WebimHistory;
import webim.WebimMenu;
import webim.WebimMessage;
import webim.WebimNotification;
import webim.WebimPresence;
import webim.WebimStatus;
import webim.service.WebimService;

@Controller()
@RequestMapping("/Webim")
public class WebimController {

	public static final String SUCCESS = "ok";

	@RequestMapping("/boot")
	public ModelAndView boot() {
		String message = "Webim Booting... ";
		System.out.println(message);
		return new ModelAndView("Webim/boot", "message", message);
	}

	@RequestMapping("/online")
	@ResponseBody
	public Map<String, Object> online(HttpServletRequest request,
			HttpServletResponse response) {
		Map<String, Object> data = new HashMap<String, Object>();
		long uid = WebimService.instance().currentUid();
		List<WebimEndpoint> buddies = WebimService.instance().getBuddies(uid);
		List<WebimGroup> groups = WebimService.instance().getGroups(uid);
		groups.addAll(WebimService.instance().getTmpGroups(uid));
		// Forward Online to IM Server
		WebimClient client = WebimService.instance().currentClient("");
		List<String> buddyIds = buddyIds(buddies);
		List<String> groupIds = groupIds(groups);
		try {
			JSONObject json = client.online(buddyIds, groupIds);
			System.out.println(json.toString());
			Map<String, String> conn = new HashMap<String, String>();
			conn.put("ticket", json.getString("ticket"));
			conn.put("domain", client.getDomain());
			conn.put("jsonpd", json.getString("jsonpd"));
			conn.put("server", json.getString("jsonpd"));
			conn.put("websocket", json.getString("websocket"));

			// Online Buddies
			Map<String, WebimEndpoint> buddyMap = new HashMap<String, WebimEndpoint>();
			for (WebimEndpoint e : buddies) {
				buddyMap.put(e.getId(), e);
			}

			JSONArray a = json.getJSONArray("buddies");
			for (int i = 0; i < a.length(); i++) {
				JSONObject o = a.getJSONObject(i);
				String n = o.getString("name");
				buddyMap.get(n).setShow("available");
			}

			Collection<WebimEndpoint> rtBuddies;
			if (WebimConfig.SHOW_UNAVAILABLE) {
				rtBuddies = buddyMap.values();
			} else {
				rtBuddies = new ArrayList<WebimEndpoint>();
				for (WebimEndpoint e : buddyMap.values()) {
					if (e.getShow() == "available")
						rtBuddies.add(e);
				}
			}

			// Groups with count
			Map<String, WebimGroup> groupMap = new HashMap<String, WebimGroup>();
			for (WebimGroup g : groups) {
				groupMap.put(g.getId(), g);
			}
			List<WebimGroup> groups1 = new ArrayList<WebimGroup>();
			a = json.getJSONArray("groups");
			for (int i = 0; i < a.length(); i++) {
				JSONObject o = (JSONObject) a.getJSONObject(i);
				String gid = o.getString("name");
				WebimGroup group = groupMap.get(gid);
				group.setCount(o.getInt("total"));
				groups1.add(group);
			}

			// {"success":true,
			// "connection":{
			// "ticket":"fcc493f7a7b17cfadbf4|admin",
			// "domain":"webim20.cn",
			// "server":"http:\/\/webim20.cn:8000\/packets"},
			// "buddies":[
			// {"uid":"5","id":"demo","nick":"demo","group":"stranger","url":"home.php?mod=space&uid=5","pic_url":"picurl","status":"","presence":"online","show":"available"}],
			// "rooms":[],
			// "server_time":1370751451399.4,
			// "user":{"uid":"1","id":"admin","nick":"admin","pic_url":"pickurl","show":"available","url":"home.php?mod=space&uid=1","status":""},
			// "new_messages":[]}

			data.put("success", true);
			data.put("connection", conn);
			data.put("buddies", rtBuddies.toArray());
			data.put("groups", groups1.toArray());
			data.put("rooms", groups1.toArray());
			data.put("server_time", System.currentTimeMillis() / 1000.0);
			data.put("user", client.getEndpoint());
		} catch (Exception e) {
			data.put("success", false);
			data.put("error_msg", "IM Server is not found");
		}
		
		return data;
	}

	@RequestMapping("/offline")
	@ResponseBody
	public String offline(HttpServletRequest request,
			HttpServletResponse response) throws Exception {
		String ticket = request.getParameter("ticket");
		WebimClient c = WebimService.instance().currentClient(ticket);
		c.offline();
		return SUCCESS;
	}

	@RequestMapping("/refresh")
	@ResponseBody
	public String refresh(HttpServletRequest request,
			HttpServletResponse response) throws Exception {
    	String ticket = request.getParameter("ticket");
        WebimClient c = WebimService.instance().currentClient(ticket);
        c.offline();
		return SUCCESS;
	}

	@RequestMapping("/message")
	@ResponseBody
	public String message(HttpServletRequest request,
			HttpServletResponse response) throws Exception {
		long uid = WebimService.instance().currentUid();
		String ticket = request.getParameter("ticket");
		String type = request.getParameter("type");
		if (type == null) type = "chat";
		String offline = request.getParameter("offline");
		String to = request.getParameter("to");
		String body = request.getParameter("body");
		String style = request.getParameter("style");
		if(style == null) style = "";

		WebimClient c = WebimService.instance().currentClient(ticket);
		WebimMessage msg = new WebimMessage(to, c.getEndpoint().getNick(),
				body, style, System.currentTimeMillis() / 1000.0);
		msg.setType(type);
		c.publish(msg);
		if (body != null && !body.startsWith("webim-event:")) {
			WebimService.instance().insertHistory(uid, offline, msg);
		}
		return SUCCESS;
	}

	@RequestMapping("/presence")
	@ResponseBody
	public String presence(HttpServletRequest request,
			HttpServletResponse response) throws Exception {
		String ticket = request.getParameter("ticket");
		String show = request.getParameter("show");
		String status = request.getParameter("status");
		if (status == null)
			status = "";

		WebimClient c = WebimService.instance().currentClient(ticket);
		c.publish(new WebimPresence(show, status));
		return SUCCESS;
	}

	@RequestMapping("/status")
	@ResponseBody
	public String status(HttpServletRequest request,
			HttpServletResponse response) throws Exception {
		String ticket = request.getParameter("ticket");
		String to = request.getParameter("to");
		String show = request.getParameter("show");
		String status = request.getParameter("status");
		if (status == null)
			status = "";
		WebimClient c = WebimService.instance().currentClient(ticket);
		WebimStatus s = new WebimStatus(to, show, status);
		c.publish(s);
		return SUCCESS;
	}

	@RequestMapping("/setting")
	@ResponseBody
	public String setting(HttpServletRequest request,
			HttpServletResponse response) {
		String data = request.getParameter("data");
		long uid = WebimService.instance().currentUid();
		WebimService.instance().updateSetting(uid, data);
		return SUCCESS;
	}

	@RequestMapping("/history")
	@ResponseBody
	public List<WebimHistory> history(HttpServletRequest request,
			HttpServletResponse response) {
		long uid = WebimService.instance().currentUid();
		String id = request.getParameter("id");
		String type = request.getParameter("type");
		List<WebimHistory> histories = WebimService.instance().getHistories(
				uid, id, type);
		return histories;
	}

	@RequestMapping("/history/clear")
	@ResponseBody
	public String clearHistory(HttpServletRequest request,
			HttpServletResponse response) {
		String id = request.getParameter("id");
		long uid = WebimService.instance().currentUid();
		WebimService.instance().clearHistories(uid, id);
		return SUCCESS;
	}

	@RequestMapping("/history/download")
	@ResponseBody
	public ModelAndView downloadHistory(HttpServletRequest request,
			HttpServletResponse response) {
		String id = request.getParameter("id");
		String type = request.getParameter("type");
		long uid = WebimService.instance().currentUid();
		List<WebimHistory> histories = WebimService.instance().getHistories(
				uid, id, type);
		return new ModelAndView("Webim/download_history", "histories",
				histories);

	}

	@RequestMapping("/members")
	@ResponseBody
	public ArrayList<Map<String,String>> members(HttpServletRequest request,
			HttpServletResponse response) throws Exception {
		String gid = request.getParameter("id");
		String ticket = request.getParameter("ticket");
    	WebimClient c = WebimService.instance().currentClient(ticket);
        JSONObject obj = c.members(gid);
        ArrayList<Map<String,String>> members = new ArrayList<Map<String,String>>();
        JSONArray array = obj.getJSONArray(gid);
        for(int i = 0; i < array.length(); i++)
        {
            Map<String,String> m = new HashMap<String,String>();
            obj = array.getJSONObject(i);
            m.put("id", obj.getString("id"));
            m.put("nick", obj.getString("nick"));
            members.add(m);
        }
    	return members;
	}

	@RequestMapping("/group/join")
	@ResponseBody
	public WebimGroup joinGroup(HttpServletRequest request,
			HttpServletResponse response) throws Exception {
		String ticket = request.getParameter("ticket");
		String id = request.getParameter("id");
		String nick = request.getParameter("nick");
		if(nick == null) nick = "";
		WebimClient c = WebimService.instance().currentClient(ticket);
		WebimGroup data = WebimService.instance().getGroup(id);
		if(data == null) {
			data = new WebimGroup(id, nick);
			data.setTemporary(true);
			JSONObject o = c.join(id);
			data.setCount(o.getInt("count"));
			//TODO: FIXME Later
			data.setPic_url("/Webim/static/images/chat.png");
		}
		return data;
	}

	@RequestMapping(value = "/group/leave", method = RequestMethod.POST)
	@ResponseBody
	public String leaveGroup(HttpServletRequest request,
			HttpServletResponse response) throws Exception {
    	String id = request.getParameter("id");
    	String ticket = request.getParameter("ticket");
    	WebimClient c = WebimService.instance().currentClient(ticket);
        c.leave(id);
		return SUCCESS;
	}

	@RequestMapping("/upload")
	@ResponseBody
	public String upload(HttpServletRequest request,
			HttpServletResponse response) {
		// TODO:
		return SUCCESS;
	}

	@RequestMapping(value = "/buddies", method = RequestMethod.GET)
	@ResponseBody
	public List<WebimEndpoint> buddies(HttpServletRequest request,
			HttpServletResponse response) {
		String ids = request.getParameter("ids");
		String ids1[] = ids.split(",");
		long id2[] = new long[ids1.length];
		for (int i = 0; i < ids1.length; i++) {
			id2[i] = Long.parseLong(ids1[i]);
		}
		return WebimService.instance().getBuddiesByIds(id2);
	}

	@RequestMapping(value = "/notifications", method = RequestMethod.GET)
	@ResponseBody
	public ArrayList<WebimNotification> notifications(
			HttpServletRequest request, HttpServletResponse response) {
		return new ArrayList<WebimNotification>();
	}
	
	@RequestMapping(value = "/menus", method = RequestMethod.GET)
	@ResponseBody
	public Collection<WebimMenu> menus() {
		long uid = WebimService.instance().currentUid();
		return WebimService.instance().getMenuList(uid);
	}
	
	private List<String> buddyIds(List<WebimEndpoint> buddies) {
		List<String> ids = new ArrayList<String>();
		for (WebimEndpoint b : buddies) {
			ids.add(b.getId());
		}
		return ids;
	}

	private List<String> groupIds(List<WebimGroup> groups) {
		List<String> ids = new ArrayList<String>();
		for (WebimGroup g : groups) {
			ids.add(g.getId());
		}
		return ids;
	}


}
