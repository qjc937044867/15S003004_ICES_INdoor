package com.qjc.IndoorNavigation.viewPage;

import java.util.ArrayList;
import java.util.List;

import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
/**
 * @ClassName: ViewPagerAdapter 
 * @Description: TODO
 * @author ���� 
 * @date 2015-3-23 ����4:06:51
 */
public class ViewPagerAdapter extends FragmentPagerAdapter {
	private List<Fragment> fragmentList=new ArrayList<Fragment>();
	public ViewPagerAdapter(FragmentManager fm) {
		super(fm);		
	}
	public ViewPagerAdapter(FragmentManager fragmentManager,List<Fragment> arrayList) {
		super(fragmentManager);
		this.fragmentList=arrayList;
	}
	@Override
	public Fragment getItem(int arg0) {
		return fragmentList.get(arg0);
	}

	@Override
	public int getCount() {
		return fragmentList.size();
	}


}
