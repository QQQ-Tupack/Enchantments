package notme.axeofkratos;

import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

public class Brain extends JavaPlugin implements Listener {

	private static Brain INSTANCE;
	
	public static Brain getInstance() {
		return INSTANCE;
	}

    @Override
    public void onEnable() {
    	
    	INSTANCE = this;

    	new Config().register();
    	new AxeOfKratos().register();
    	new JustCauseFeatures().register();
    	new ChickenParachute().register();
    	new Parrying().register();
    	new WolfMyFriend().register();
    	new DoubleJump().register();
    }
    
    @Override
    public void onDisable() {
    }
}