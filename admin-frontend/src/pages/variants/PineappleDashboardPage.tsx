import React, { useState, useEffect } from 'react';
import { api } from '../../api/client';
import { Flame, Clock, RefreshCw } from 'lucide-react';
import { VariantProfitSection } from '../../components/variant/VariantProfitSection';

export const PineappleDashboardPage: React.FC = () => {
  const [data, setData] = useState<any>(null);
  const [matchType, setMatchType] = useState<'ALL' | 'REAL_VS_REAL' | 'REAL_VS_BOT'>('ALL');
  const [playerCount, setPlayerCount] = useState<'ALL' | '2' | '6'>('ALL');

  const fetchMetrics = async () => {
    try {
      const params = new URLSearchParams();
      if (matchType !== 'ALL') params.append('matchType', matchType);
      if (playerCount !== 'ALL') params.append('playerCount', playerCount);
      const queryStr = params.toString() ? `?${params.toString()}` : '';

      const res = await api.get(`/variants/PINEAPPLE/metrics${queryStr}`);
      if (res.data?.success) {
        setData(res.data);
      }
    } catch (err) {
      console.error(err);
    }
  };

  useEffect(() => {
    fetchMetrics();
    const interval = setInterval(fetchMetrics, 7000);
    return () => clearInterval(interval);
  }, [matchType, playerCount]);

  const metrics = data?.metrics || {};
  const financials = data?.financials || {
    totalProfit: 0,
    realVsRealProfit: 0,
    realVsBotProfit: 0,
    realVsRealMatchesCount: 0,
    realVsBotMatchesCount: 0,
    twoPlayerMatchesCount: 0,
    sixPlayerMatchesCount: 0,
    totalMatchesCount: 0,
  };
  const outcomes = metrics?.outcomeBreakdown || {};
  const recent = data?.recentMatches || [];

  return (
    <div>
      {/* Header */}
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '18px' }}>
        <div>
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <span className="badge badge-amber" style={{ fontSize: '12px' }}>PINEAPPLE</span>
            <h1 style={{ fontSize: '18px', fontWeight: 700, color: '#fff' }}>
              Pineapple Poker Dedicated Dashboard
            </h1>
          </div>
          <p style={{ fontSize: '12px', color: 'var(--text-muted)', marginTop: '2px' }}>
            3-card Pineapple / Crazy Pineapple telemetry, discard timing & pot rake analytics
          </p>
        </div>

        <button onClick={fetchMetrics} className="btn" style={{ fontSize: '11.5px', padding: '5px 10px' }}>
          <RefreshCw size={13} />
          <span>Refresh</span>
        </button>
      </div>

      {/* Operational KPI Cards */}
      <div style={{ marginBottom: '10px' }}>
        <h2 style={{ fontSize: '13px', fontWeight: 600, color: 'var(--text-muted)', textTransform: 'uppercase', letterSpacing: '0.5px' }}>
          Table Telemetry & Hand Outlines
        </h2>
      </div>

      <div className="grid-kpis" style={{ marginBottom: '22px' }}>
        <div className="kpi-card" style={{ borderTop: '2px solid var(--accent-amber)' }}>
          <div className="kpi-card-header">
            <span className="kpi-card-title">Today's Pineapple Hands</span>
            <Flame size={16} color="var(--accent-amber)" />
          </div>
          <div className="kpi-card-value font-mono">{metrics.totalToday ?? 0}</div>
          <div className="kpi-card-delta" style={{ color: 'var(--text-dim)' }}>
            All-Time: {metrics.totalAllTime ?? 0}
          </div>
        </div>

        <div className="kpi-card" style={{ borderTop: '2px solid var(--accent-amber)' }}>
          <div className="kpi-card-header">
            <span className="kpi-card-title">Average Hand Duration</span>
            <Clock size={16} color="var(--accent-cyan)" />
          </div>
          <div className="kpi-card-value font-mono">{metrics.avgDurationSeconds ?? 0}s</div>
          <div className="kpi-card-delta" style={{ color: 'var(--accent-amber)' }}>
            Pre-flop discard action
          </div>
        </div>

        <div className="kpi-card" style={{ borderTop: '2px solid var(--accent-amber)' }}>
          <div className="kpi-card-header">
            <span className="kpi-card-title">Showdown Win Rate</span>
            <span className="badge badge-amber">Active</span>
          </div>
          <div className="kpi-card-value font-mono">
            {outcomes.WON ? Math.round((outcomes.WON / (metrics.totalAllTime || 1)) * 100) : 0}%
          </div>
          <div className="kpi-card-delta font-mono" style={{ color: 'var(--text-muted)' }}>
            Won: {outcomes.WON ?? 0} | Folds: {outcomes.DROPPED ?? 0}
          </div>
        </div>

        <div className="kpi-card" style={{ borderTop: '2px solid var(--accent-amber)' }}>
          <div className="kpi-card-header">
            <span className="kpi-card-title">Rake Retained (INR)</span>
            <span className="badge badge-gold">5% Pot Rake</span>
          </div>
          <div className="kpi-card-value font-mono text-gold">
            ₹{(financials.totalProfit ?? 0).toLocaleString('en-IN', { minimumFractionDigits: 2 })}
          </div>
          <div className="kpi-card-delta" style={{ color: 'var(--accent-amber)' }}>
            Net platform commission
          </div>
        </div>
      </div>

      {/* P&L Breakdown Section */}
      <VariantProfitSection
        financials={financials}
        matches={recent}
        matchType={matchType}
        onMatchTypeChange={setMatchType}
        playerCount={playerCount}
        onPlayerCountChange={setPlayerCount}
        variantThemeColor="var(--accent-amber)"
      />
    </div>
  );
};
