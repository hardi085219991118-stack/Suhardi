import React from 'react';
import { Home, Map, Flame, Info, Ruler } from 'lucide-react';
import { AppBottomNavTab } from '../types';

interface NavigationProps {
  currentTab: AppBottomNavTab;
  onTabSelected: (tab: AppBottomNavTab) => void;
  hotspotCount: number;
}

export const Navigation: React.FC<NavigationProps> = ({
  currentTab,
  onTabSelected,
  hotspotCount,
}) => {
  const tabs = [
    { id: AppBottomNavTab.BERANDA, label: 'Beranda', icon: Home },
    { id: AppBottomNavTab.PETA, label: 'Peta', icon: Map },
    { id: AppBottomNavTab.TITIK_PANAS, label: 'Titik Panas', icon: Flame, badge: hotspotCount },
    { id: AppBottomNavTab.PENGUKURAN_TANAH, label: 'Ukur Tanah', icon: Ruler },
    { id: AppBottomNavTab.INFO, label: 'Info', icon: Info },
  ];

  return (
    <>
      {/* Top Header Bar for branding & quick status */}
      <header className="sticky top-0 z-30 w-full bg-[#0d1520]/95 backdrop-blur border-b border-slate-800 px-4 py-3 flex items-center justify-between">
        <div className="flex items-center gap-2.5">
          <div className="w-8 h-8 rounded-full bg-orange-500/20 flex items-center justify-center text-orange-500 border border-orange-500/30">
            <Flame className="w-5 h-5" />
          </div>
          <div>
            <h1 className="text-sm font-extrabold tracking-wide text-slate-100 uppercase">
              Hardi Mantangai Fire Now
            </h1>
            <p className="text-[10px] text-slate-400 font-mono tracking-wider">
              ZERO-DUMMY &bull; NASA FIRMS NRT
            </p>
          </div>
        </div>

        <div className="hidden md:flex items-center gap-1">
          {tabs.map((tab) => {
            const Icon = tab.icon;
            const isActive = currentTab === tab.id;
            return (
              <button
                key={tab.id}
                onClick={() => onTabSelected(tab.id)}
                className={`relative px-3.5 py-1.5 rounded-lg text-xs font-semibold flex items-center gap-2 transition-all ${
                  isActive
                    ? 'bg-emerald-500/15 text-emerald-400 border border-emerald-500/30'
                    : 'text-slate-400 hover:text-slate-200 hover:bg-slate-800/60'
                }`}
              >
                <Icon className="w-4 h-4" />
                <span>{tab.label}</span>
                {tab.badge !== undefined && tab.badge > 0 && (
                  <span className="ml-1 px-1.5 py-0.2 bg-orange-500 text-white text-[10px] font-bold rounded-full">
                    {tab.badge}
                  </span>
                )}
              </button>
            );
          })}
        </div>
      </header>

      {/* Mobile Bottom Navigation Bar matching original Android MyNavigationBottomBar */}
      <nav className="md:hidden fixed bottom-0 left-0 right-0 z-40 bg-[#0d1520] border-t border-slate-800 px-1 py-1 shadow-2xl">
        <div className="grid grid-cols-5 gap-0.5">
          {tabs.map((tab) => {
            const Icon = tab.icon;
            const isActive = currentTab === tab.id;
            return (
              <button
                key={tab.id}
                onClick={() => onTabSelected(tab.id)}
                className={`relative flex flex-col items-center justify-center py-1.5 px-1 rounded-xl transition-all ${
                  isActive
                    ? 'text-emerald-400 bg-emerald-500/10'
                    : 'text-slate-400 hover:text-slate-200'
                }`}
              >
                <div className="relative">
                  <Icon className="w-5 h-5" />
                  {tab.badge !== undefined && tab.badge > 0 && (
                    <span className="absolute -top-1.5 -right-2.5 px-1 min-w-[16px] h-4 bg-orange-500 text-white text-[9px] font-bold rounded-full flex items-center justify-center">
                      {tab.badge > 99 ? '99+' : tab.badge}
                    </span>
                  )}
                </div>
                <span
                  className={`text-[10px] mt-1 tracking-tight ${
                    isActive ? 'font-bold' : 'font-normal'
                  }`}
                >
                  {tab.label}
                </span>
              </button>
            );
          })}
        </div>
      </nav>
    </>
  );
};
